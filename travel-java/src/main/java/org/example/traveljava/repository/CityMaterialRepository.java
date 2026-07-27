package org.example.traveljava.repository;

import org.example.traveljava.entity.CityMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CityMaterialRepository extends JpaRepository<CityMaterial, Long> {

    Optional<CityMaterial> findByCityCode(String cityCode);

    Optional<CityMaterial> findByCityName(String cityName);

    List<CityMaterial> findByProvinceCode(String provinceCode);

    List<CityMaterial> findByMaterialLevel(Integer level);

    boolean existsByCityCode(String cityCode);
}
