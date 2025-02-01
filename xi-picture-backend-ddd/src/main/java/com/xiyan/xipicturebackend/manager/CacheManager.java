package com.xiyan.xipicturebackend.manager;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.xiyan.xipicture.interfaces.dto.picture.PictureQueryRequest;
import com.xiyan.xipicture.interfaces.vo.picture.PictureVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
public class CacheManager {

    @Resource
    private CaffeineManager caffeineManager;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 从本地缓存获取数据
     *
     * @param cacheKey
     * @return
     */
    public Page<PictureVO> getCacheDataByCaffeine(String cacheKey) {
        Cache<String, String> localCache = caffeineManager.getLocalCache();
        String cachedValue = localCache.getIfPresent(cacheKey);
        if (cachedValue != null) {
            // 如果缓存命中，返回结果
            // 缓存结果比数据库查询的结果大小要小一点，因为这里涉及到转换的过程，这个过程没有将为 null 的数据缓存
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return cachedPage;
        }
        return new Page<PictureVO>();
    }

    /**
     * 从 Redis 缓存获取数据
     *
     * @param cacheKey
     * @return
     */
    public Page<PictureVO> getCacheDataByRedis(String cacheKey) {
        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
        String cachedValue = opsForValue.get(cacheKey);
        if (cachedValue != null) {
            // 如果缓存命中,返回结果
            // 缓存结果比数据库查询的结果大小要小一点，因为这里涉及到转换的过程，这个过程没有将为 null 的数据缓存
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return cachedPage;
        }
        return new Page<PictureVO>();
    }

    /**
     * 更新本地缓存
     *
     * @param cacheKey
     * @param cachePage
     */
    public void updateCaffeineCache(String cacheKey, Page<PictureVO> cachePage) {
        Cache<String, String> localCache = caffeineManager.getLocalCache();
        String cachedValue = JSONUtil.toJsonStr(cachePage);
        localCache.put(cacheKey, cachedValue);
    }

    /**
     * 更新 Redis 缓存
     *
     * @param cacheKey
     * @param cachePage
     */
    public void updateRedisCache(String cacheKey, Page<PictureVO> cachePage, int cacheExpireTime) {
        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
        String cachedValue = JSONUtil.toJsonStr(cachePage);
        opsForValue.set(cacheKey, cachedValue, cacheExpireTime, TimeUnit.SECONDS);
    }

    /**
     * 获取缓存键
     *
     * @param pictureQueryRequest
     * @return
     */
    public String getCacheKey(PictureQueryRequest pictureQueryRequest) {
        // 查询缓存，缓存中没有，再查询数据库
        // 构建缓存的 key
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = String.format("xipicture:listPictureVOByPage:%s", hashKey);
        return cacheKey;
    }
}
