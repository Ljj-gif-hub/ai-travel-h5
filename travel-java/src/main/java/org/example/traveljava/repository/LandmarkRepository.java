package org.example.traveljava.repository;

import org.example.traveljava.entity.Landmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 地标数据访问层
 * 提供地标信息的CRUD操作和按城市、类型筛选的自定义查询
 */
@Repository
public interface LandmarkRepository extends JpaRepository<Landmark, Long> {

    /**
     * 根据城市名称查询所有地标
     * 用于加载某一城市的完整地标地图数据
     * @param city 城市名称
     * @return 该城市下的所有地标列表
     */
    List<Landmark> findByCity(String city);

    /**
     * 根据城市和地标类型查询
     * 用于分类展示（如仅显示景点、仅显示地铁站等）
     * @param city 城市名称
     * @param type 地标类型（"attraction"、"metro"、"landmark"）
     * @return 符合条件的地标列表
     */
    List<Landmark> findByCityAndType(String city, String type);
}
