package dev.erkut.stockservice.stock.persistence;

import dev.erkut.stockservice.stock.domain.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockItemRepository extends JpaRepository<StockItem, UUID> {
}
