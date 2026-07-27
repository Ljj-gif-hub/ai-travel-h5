package org.example.traveljava.repository;

import org.example.traveljava.entity.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * 酒店数据访问层
 * 提供酒店信息的CRUD操作和自定义查询方法
 */
@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long> {

    /**
     * 根据城市名称查询所有酒店
     * @param city 城市名称
     * @return 该城市下的所有酒店列表
     */
    List<Hotel> findByCity(String city);

    /**
     * 根据城市和区域查询酒店
     * 用于按商圈/行政区筛选住宿选项
     * @param city 城市名称
     * @param district 区域名称
     * @return 符合条件的酒店列表
     */
    List<Hotel> findByCityAndDistrict(String city, String district);

    /**
     * 根据城市和价格区间查询酒店
     * 用于用户预算范围内的住宿筛选
     * @param city 城市名称
     * @param min 最低价格（含）
     * @param max 最高价格（含）
     * @return 价格区间内的酒店列表
     */
    List<Hotel> findByCityAndPricePerNightBetween(String city, BigDecimal min, BigDecimal max);
}
