package org.example.traveljava.repository;

import org.example.traveljava.entity.AttractionImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttractionImageRepository extends JpaRepository<AttractionImage, Long> {

    Optional<AttractionImage> findByAttractionName(String attractionName);

    boolean existsByAttractionName(String attractionName);
}
