package finki.das.stocksdataapplication.repository;

import finki.das.stocksdataapplication.StockData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

//@Repository
public interface StockDataRepository extends MongoRepository<StockData, String> {
    List<StockData> findBySymbol(String symbol);
}
