package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);

    List<Category> findByType(CategoryType type);
}
