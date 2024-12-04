package finki.das.stocksdataapplication.service;

import finki.das.stocksdataapplication.StockData;
import finki.das.stocksdataapplication.repository.StockDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StockDataService {

    private final StockDataRepository stockDataRepository;

    @Autowired
    public StockDataService(StockDataRepository stockDataRepository) {
        this.stockDataRepository = stockDataRepository;
    }

    public List<StockData> findAll() {
        return stockDataRepository.findAll();
    }

    public StockData save(StockData stockData) {
        return stockDataRepository.save(stockData);
    }

    public Optional<StockData> findById(String id) {
        return stockDataRepository.findById(id);
    }

    public void deleteById(String id) {
        stockDataRepository.deleteById(id);
    }
}
