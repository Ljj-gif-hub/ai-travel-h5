package org.example.traveljava.repository;

import org.example.traveljava.entity.CityImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CityImageRepository extends JpaRepository<CityImage, Long> {

    Optional<CityImage> findByCityName(String cityName);

    boolean existsByCityName(String cityName);
}
