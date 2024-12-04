package finki.das.stocksdataapplication.controller;

import finki.das.stocksdataapplication.StockData;
import finki.das.stocksdataapplication.service.StockDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/stocks")
public class StockDataController {

    private final StockDataService stockDataService;

    @Autowired
    public StockDataController(StockDataService stockDataService) {
        this.stockDataService = stockDataService;
    }

    @GetMapping
    public List<StockData> getAllStockData() {
        return stockDataService.findAll();
    }

    @PostMapping
    public StockData createStockData(@RequestBody StockData stockData) {
        return stockDataService.save(stockData);
    }

    @GetMapping("/{id}")
    public StockData getStockDataById(@PathVariable String id) {
        return stockDataService.findById(id).get();
    }

    @DeleteMapping("/{id}")
    public void deleteStockData(@PathVariable String id) {
        stockDataService.deleteById(id);
    }
}
