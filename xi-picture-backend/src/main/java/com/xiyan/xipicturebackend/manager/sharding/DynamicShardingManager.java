package com.xiyan.xipicturebackend.manager.sharding;

import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import com.xiyan.xipicturebackend.model.entity.Space;
import com.xiyan.xipicturebackend.model.enums.SpaceLevelEnum;
import com.xiyan.xipicturebackend.model.enums.SpaceTypeEnum;
import com.xiyan.xipicturebackend.service.SpaceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.driver.jdbc.core.connection.ShardingSphereConnection;
import org.apache.shardingsphere.infra.metadata.database.rule.ShardingSphereRuleMetaData;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.rule.ShardingRule;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

//@Component  // 关闭分库分表
@Slf4j
public class DynamicShardingManager {

    @Resource
    private DataSource dataSource;

    @Resource
    private SpaceService spaceService;

    private static final String LOGIC_TABLE_NAME = "picture";

    private static final String DATABASE_NAME = "logic_db"; // 配置文件中的数据库名称

    @PostConstruct
    public void initialize() {
        log.info("初始化动态分表配置...");
        updateShardingTableNodes();
    }

    /**
     * 获取所有动态表名，包括初始表 picture 和分表 picture_{spaceId}
     */
    private Set<String> fetchAllPictureTableNames() {
        // 为了测试方便，直接对所有团队空间分表（实际上线改为仅对旗舰版生效）
        Set<Long> spaceIds = spaceService.lambdaQuery()
                .eq(Space::getSpaceType, SpaceTypeEnum.TEAM.getValue())
                .list()
                .stream()
                .map(Space::getId)
                .collect(Collectors.toSet());
        Set<String> tableNames = spaceIds.stream()
                .map(spaceId -> LOGIC_TABLE_NAME + "_" + spaceId)
                .collect(Collectors.toSet());
        tableNames.add(LOGIC_TABLE_NAME); // 添加初始逻辑表
        return tableNames;
    }

    /**
     * 更新 ShardingSphere 的 actual-data-nodes 动态表名配置
     */
    private void updateShardingTableNodes() {
        Set<String> tableNames = fetchAllPictureTableNames();
        // xi_picture.picture_112321321,xi_picture.picture_1123213123
        String newActualDataNodes = tableNames.stream()
                .map(tableName -> "xi_picture." + tableName) // 确保前缀合法
                .collect(Collectors.joining(","));
        log.info("动态分表 actual-data-nodes 配置: {}", newActualDataNodes);
        ContextManager contextManager = getContextManager();
        ShardingSphereRuleMetaData ruleMetaData = contextManager.getMetaDataContexts()
                .getMetaData()
                .getDatabases()
                .get(DATABASE_NAME)
                .getRuleMetaData();
        Optional<ShardingRule> shardingRule = ruleMetaData.findSingleRule(ShardingRule.class);
        if (shardingRule.isPresent()) {
            ShardingRuleConfiguration ruleConfig = (ShardingRuleConfiguration) shardingRule.get().getConfiguration();
            List<ShardingTableRuleConfiguration> updatedRules = ruleConfig.getTables()
                    .stream()
                    .map(oldTableRule -> {
                        if (LOGIC_TABLE_NAME.equals(oldTableRule.getLogicTable())) {
                            ShardingTableRuleConfiguration newTableRuleConfig = new ShardingTableRuleConfiguration(LOGIC_TABLE_NAME, newActualDataNodes);
                            newTableRuleConfig.setDatabaseShardingStrategy(oldTableRule.getDatabaseShardingStrategy());
                            newTableRuleConfig.setTableShardingStrategy(oldTableRule.getTableShardingStrategy());
                            newTableRuleConfig.setKeyGenerateStrategy(oldTableRule.getKeyGenerateStrategy());
                            newTableRuleConfig.setAuditStrategy(oldTableRule.getAuditStrategy());
                            return newTableRuleConfig;
                        }
                        return oldTableRule;
                    })
                    .collect(Collectors.toList());
            ruleConfig.setTables(updatedRules);
            contextManager.alterRuleConfiguration(DATABASE_NAME, Collections.singleton(ruleConfig));
            contextManager.reloadDatabase(DATABASE_NAME);
            log.info("动态分表规则更新成功！");
        } else {
            log.error("未找到 ShardingSphere 的分片规则配置，动态分表更新失败。");
        }
    }

    /**
     * 动态创建空间图片分表
     *
     * @param space
     */
    public void createSpacePictureTable(Space space) {
        // 仅为旗舰版团队空间创建分表
        if (space.getSpaceType() == SpaceTypeEnum.TEAM.getValue() && space.getSpaceLevel() == SpaceLevelEnum.FLAGSHIP.getValue()) {
            Long spaceId = space.getId();
            String tableName = LOGIC_TABLE_NAME + "_" + spaceId;
            // 创建新表
            String createTableSql = "CREATE TABLE " + tableName + " LIKE " + LOGIC_TABLE_NAME;
            try {
                // 注意，想要使用 MyBatis Plus 的 SqlRunner，必须要开启配置
                SqlRunner.db().update(createTableSql);
                // 更新分表
                updateShardingTableNodes();
            } catch (Exception e) {
                e.printStackTrace();
                log.error("创建图片空间分表失败，空间 id = {}", space.getId());
            }
        }
    }

    /**
     * 获取 ShardingSphere ContextManager
     */
    private ContextManager getContextManager() {
        try (ShardingSphereConnection connection = dataSource.getConnection().unwrap(ShardingSphereConnection.class)) {
            return connection.getContextManager();
        } catch (SQLException e) {
            throw new RuntimeException("获取 ShardingSphere ContextManager 失败", e);
        }
    }

    // 现阶段先不用分库分表，关闭分库分表（可选）
    // todo 11.3 问题：测试数据插入。插入时如果想往公共空间插入（不指定 spaceId），就会报错，因为 ShardingSphere 不知道要把数据插入到哪个表中。
    // 这就意味着，如果你要使用分表，spaceId 必须不能为 null！
    // 为了解决这个问题，插入时一定要指定 spaceId，可以约定公共空间的 spaceId 都为 0，并且在插入时为 spaceId 设置默认值 0。
    // 注意，增删改查时都要补充 spaceId，才能避免报错和多表查询影响效率。
    // 注意，分表后，picture 的 spaceId 将不能修改！！！
    // 经过开发和测试，会发现动态分库分表的实现非常麻烦。某些单表的查询性能是高了，但整体查询的性能可能会减少，所以原则上 非必要不分表，一定要找到合适的应用场景。
}