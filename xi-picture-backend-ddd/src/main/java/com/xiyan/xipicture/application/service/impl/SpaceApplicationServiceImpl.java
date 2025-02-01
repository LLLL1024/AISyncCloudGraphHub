package com.xiyan.xipicture.application.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiyan.xipicture.domain.space.service.SpaceDomainService;
import com.xiyan.xipicture.infrastructure.exception.BusinessException;
import com.xiyan.xipicture.infrastructure.exception.ErrorCode;
import com.xiyan.xipicture.infrastructure.exception.ThrowUtils;
import com.xiyan.xipicture.interfaces.dto.space.SpaceAddRequest;
import com.xiyan.xipicture.interfaces.dto.space.SpaceQueryRequest;
import com.xiyan.xipicture.domain.space.entity.Space;
import com.xiyan.xipicture.domain.space.entity.SpaceUser;
import com.xiyan.xipicture.domain.user.entity.User;
import com.xiyan.xipicture.domain.space.valueobject.SpaceLevelEnum;
import com.xiyan.xipicture.domain.space.valueobject.SpaceRoleEnum;
import com.xiyan.xipicture.domain.space.valueobject.SpaceTypeEnum;
import com.xiyan.xipicture.interfaces.vo.space.SpaceVO;
import com.xiyan.xipicture.interfaces.vo.user.UserVO;
import com.xiyan.xipicture.application.service.SpaceApplicationService;
import com.xiyan.xipicture.infrastructure.mapper.SpaceMapper;
import com.xiyan.xipicture.application.service.SpaceUserApplicationService;
import com.xiyan.xipicture.application.service.UserApplicationService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author xiyan
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2025-01-18 16:09:25
*/
@Service
public class SpaceApplicationServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceApplicationService {

    @Resource
    private SpaceDomainService spaceDomainService;

    @Resource
    private UserApplicationService userApplicationService;

    @Resource
    private SpaceUserApplicationService spaceUserApplicationService;

    @Resource
    private TransactionTemplate transactionTemplate;

    // 为了方便部署，注释掉分表
//    @Resource
//    @Lazy  // 防止循环依赖
//    private DynamicShardingManager dynamicShardingManager;

    /**
     * 创建空间
     * todo 7.1 用户注册成功时，可以自动创建空间。即使创建失败了，也可以手动创建作为兜底。（目前没啥必要）
     * todo 7.2 管理员可以为某个用户创建空间（目前没啥必要）
     * todo 7.3 本地锁改为分布式锁，可以基于 Redisson 实现。（可以看之前做的 AI 答题应用平台项目）。
     *
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 1. 填充参数默认值
        // 转换实体类和 DTO
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        if (StrUtil.isBlank(space.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (space.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        if (space.getSpaceType() == null) {
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        // 填充容量和大小
        this.fillSpaceBySpaceLevel(space);
        // 2. 校验参数
        space.validSpace(true);
        // 3. 校验权限，非管理员只能创建普通级别的空间
        Long userId = loginUser.getId();
        space.setUserId(userId);
        if (SpaceLevelEnum.COMMON.getValue() != space.getSpaceLevel() && !loginUser.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }
        // 4. 控制同一用户只能创建一个私有空间、以及一个团队空间（不同的用户可以同时创建，每个用户一把锁，而不是整个方法一把锁，这样可以提高性能）
        // .intern() 将字符串添加到 JVM 的字符串常量池（如果池中不存在该字符串），并返回池中的唯一引用。这意味着所有相同值的字符串都会指向内存中的同一个对象。
        // 这里的意图可能是将 String 用作同步锁对象。通过使用 intern()，可以确保所有使用相同 userId 的线程都会引用内存中的同一个 String 对象，从而实现共享锁。
        String lock = String.valueOf(userId).intern();
        synchronized (lock) {
            Long newSpaceId = transactionTemplate.execute(status -> {
                // 判断是否已有空间
                boolean exists = this.lambdaQuery()
                        .eq(Space::getUserId, userId)
                        .eq(Space::getSpaceType, space.getSpaceType())
                        .exists();
                // 如果已有空间，就不能再创建（只能创建一个私有空间、以及一个团队空间）
                ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户每类空间只能创建一个");
                // 创建
                boolean result = this.save(space);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "保存空间到数据库失败");
                // 创建成功后，如果是团队空间，关联新增团队成员记录
                // 创建团队空间时自动新增成员记录
                // 用户在创建团队空间时，会默认作为空间的管理员，需要在空间成员表中新增一条记录。
                if (SpaceTypeEnum.TEAM.getValue() == space.getSpaceType()) {
                    SpaceUser spaceUser = new SpaceUser();
                    spaceUser.setSpaceId(space.getId());
                    spaceUser.setUserId(userId);
                    spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                    result = spaceUserApplicationService.save(spaceUser);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建团队成员记录失败");
                }
//                // 创建分表（仅对团队空间生效）为方便部署，暂时不使用
//                dynamicShardingManager.createSpacePictureTable(space);
                // 返回新写入的数据 id
                return space.getId();
            });
            return Optional.ofNullable(newSpaceId).orElse(-1L);
        }
    }

    /**
     * 获取空间包装类（单条）
     *
     * @param space
     * @param request
     * @return
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 对象转封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联查询用户信息
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userApplicationService.getUserById(userId);
            UserVO userVO = userApplicationService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    /**
     * 获取空间包装类（分页）
     *
     * @param spacePage
     * @param request
     * @return
     */
    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // 对象列表 => 封装对象列表
        List<SpaceVO> spaceVOList = spaceList.stream()
                .map(SpaceVO::objToVo)
                .collect(Collectors.toList());
        // 1. 关联查询用户信息
        // 1,2,3,4
        // Set集合是无序的，通过 listByIds 去获取用户列表，那么对应的用户列表就不是之前 spaceList 里面对应的 userId 的顺序，因此就通过 userId 去分组即可
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        // 1 => user1, 2 => user2
        Map<Long, List<User>> userIdUserListMap = userApplicationService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVO.setUser(userApplicationService.getUserVO(user));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    /**
     * 获取查询对象
     *
     * @param spaceQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        return spaceDomainService.getQueryWrapper(spaceQueryRequest);
    }

    /**
     * 根据空间级别填充空间对象
     *
     * @param space
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        spaceDomainService.fillSpaceBySpaceLevel(space);
    }

    /**
     * 校验空间权限
     *
     * @param loginUser
     * @param space
     */
    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        spaceDomainService.checkSpaceAuth(loginUser, space);
    }
}




