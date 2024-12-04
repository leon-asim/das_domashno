package finki.das.stocksdataapplication;

import finki.das.stocksdataapplication.repository.StockDataRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class StockDataFetcher {

    private static final String BASE_URL = "https://www.mse.mk/mk/stats/symbolhistory/";
    private static final String FOLDER_NAME = "Fetched_Files";
    private static final String DATA_FILE = "_stock_data.csv"; // Store data in a CSV file
    private static final String LAST_SAVED_DATE_FILE = "lastsaveddate.txt";

    @Autowired
    private StockDataRepository stockDataRepository;


    public static class DropdownExtractor {
        public static List<String> getDropdownValues() throws IOException {
            List<String> values = new ArrayList<>(); // Create a list to store option values
            Document doc = Jsoup.connect(BASE_URL + "STB").get();
            Element dropdown = doc.getElementById("Code");
            if (dropdown != null) {
                Elements options = dropdown.getElementsByTag("option");
                for (Element option : options) {
                    String value = option.attr("value");
                    if (!value.matches(".*\\d.*")) {
                        values.add(value);
                    }
                }
            }
            return values; // Return the list of values without numbers
        }
    }


    public void fetchHistoricalData(String symbol, String fromDate, String toDate, WebDriver driver) {
        try {
            String url = BASE_URL + symbol;
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.pollingEvery(Duration.ofMillis(500));

            // Fill in date fields and select stock code
            fillDateFields(wait, fromDate, toDate);
            selectStockCode(wait, symbol);

            // Click the "Прикажи" button
            WebElement submitButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("input[type='submit'][value='Прикажи']")));
            submitButton.click();

            // Check if the "no-results" div is displayed
            if (driver.findElements(By.cssSelector("div.no-results")).size() > 0) {
                System.out.println("No data available for " + symbol + " from " + fromDate + " to " + toDate);
                return;
            }
            // Proceed to fetch data if "results" table is displayed
            WebElement resultsTable = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("table#resultsTable")));
            System.out.println("Data available for " + symbol + " from " + fromDate + " to " + toDate);

            // Fetch and save data for the year
            Set<String> uniqueData = new HashSet<>();
            fetchData(driver, uniqueData, symbol);

        } catch (TimeoutException | InterruptedException e) {
            System.out.println("No data available for: " + symbol);
        }
    }

    public void fetchHistoricalDataTenYears(String symbol,WebDriver driver) {
        try {
            // Load the base URL for the symbol
            String url = BASE_URL + symbol;
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.pollingEvery(Duration.ofMillis(500));

            // Loop through each year, setting date ranges for each request
            for (int i = 11; i >0; i--) {
                int year = LocalDate.now().getYear() - i + 1;
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                LocalDate today = LocalDate.now();
                String toDateStr = today.format(formatter);
                int todayYear = today.getYear();
                String fromDate="";
                String toDate="";
                if(i==1){
                    fromDate = "01.01."+todayYear;
                    toDate = toDateStr;
                }else{
                    fromDate = "01.01." + year;
                    toDate = "31.12." + year;
                }
                // Fill in the date fields and select the stock code for each year
                fillDateFields(wait, fromDate, toDate);

                // Click the "Прикажи" button
                WebElement submitButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("input[type='submit'][value='Прикажи']")));
                submitButton.click();

                if (driver.findElements(By.cssSelector("div.no-results")).size() > 0) {
                    //System.out.println("No data available for " + symbol + " in year " + year);
                    continue; // Skip to the next year if no data is available
                }

                // Wait for the results table to load
                WebElement resultsTable = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("table#resultsTable")));
                //System.out.println("Data available for " + symbol + " from " + fromDate + " to " + toDate);

                // Fetch and save data for the year
                Set<String> uniqueData = new HashSet<>();
                fetchData(driver, uniqueData, symbol);

            }
        }catch (TimeoutException | InterruptedException e) {
            System.out.println("No data available for " + symbol);
        }
    }

    private String getLastSavedDate() {
        try {
            File file = new File(LAST_SAVED_DATE_FILE);
            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    return reader.readLine();
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading last saved date: " + e.getMessage());
        }
        return ""; // Return empty string if file doesn't exist or error occurs
    }

    private void updateLastSavedDate() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LAST_SAVED_DATE_FILE))) {
            String todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            writer.write(todayDate);
        } catch (IOException e) {
            System.out.println("Error updating last saved date: " + e.getMessage());
        }
    }

    private void saveWordByWord(String symbol, String[] words) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FOLDER_NAME + "\\" + symbol + DATA_FILE, true))) {
            for (int i = 0; i < words.length; i++) {
                String word = words[i].trim();
                writer.write("\"" + word +"\"");
                if (i < words.length - 1) {
                    writer.write(","); // Separate with a |
                }
            }
            writer.newLine();

        } catch (IOException e) {
            System.out.println("Error occurred in saveWORDbyWord" + e);
        }
    }


    private void fillDateFields(WebDriverWait wait, String fromDate, String toDate) {
        WebElement fromDateField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("FromDate")));
        fromDateField.clear();
        fromDateField.sendKeys(fromDate);

        WebElement toDateField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("ToDate")));
        toDateField.clear();
        toDateField.sendKeys(toDate);
    }

    private void selectStockCode(WebDriverWait wait, String symbol) {
        WebElement codeDropdown = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("Code")));
        codeDropdown.click();
        WebElement option = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//option[@value='" + symbol + "']")));
        option.click();
    }



    private void fetchData(WebDriver driver, Set<String> uniqueData, String symbol) throws InterruptedException {
        boolean hasMoreData = true;
        List<WebElement> previousRows = driver.findElements(By.cssSelector("table tbody tr"));

        while (hasMoreData) {
            try {
                Document doc = Jsoup.parse(driver.getPageSource());
                Elements rows = doc.select("table tbody tr");

                if (rows.isEmpty()) {
                    System.out.println("No data found for the given date range.");
                    break;
                }

                for (Element row : rows) {
                    Elements tds = row.select("td");
                    if (tds.size() >= 9) {
                        String mak = tds.get(2).text();
                        String min = tds.get(3).text();

                        if (mak.isEmpty() && min.isEmpty()) {
                            continue;
                        } else {
                            String entry = String.join("\t", tds.eachText());
                            if (uniqueData.add(entry)) {
                                String[] rowData = new String[tds.size()];
                                for (int i = 0; i < tds.size(); i++) {
                                    rowData[i] = tds.get(i).text();
                                }
                                //saveDataToMongoDB(symbol, rowData);
                                saveWordByWord(symbol, rowData);
                            }
                        }
                    }
                }
                // Check if new rows have been loaded
                List<WebElement> newRows = driver.findElements(By.cssSelector("table tbody tr"));
                hasMoreData = newRows.size() > previousRows.size();
                previousRows = newRows;

            } catch (Exception e) {
                System.out.println("Error while fetching data for " + symbol + ": " + e.getMessage());
                break;  // Stop if an error occurs
            }
        }
    }

    private void saveDataToMongoDB(String symbol, String[] rowData) {
        StockData stockData = new StockData();
        stockData.setSymbol(symbol);
        stockData.setDate(rowData[0]);  // Assuming the date is the first element in the row
        stockData.setLastTransaction(rowData[1]);   // Assuming 'mak' is the second element in the row
        stockData.setMaks(rowData[2]);   // Assuming 'min' is the third element in the row
        stockData.setMin(rowData[3]);
        stockData.setAveragePrice(rowData[4]);
        stockData.setPromPercent(rowData[5]);
        stockData.setQuantity(rowData[6]);
        stockData.setRevenueBEST(rowData[7]);
        stockData.setTotalRevenue(rowData[8]);


        // Save the StockData object to MongoDB
        stockDataRepository.save(stockData);
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        long startTime = System.currentTimeMillis();
        int counter = 0;

        // Ensure the data folder exists
        File folderFetcher = new File(FOLDER_NAME);
        if (!folderFetcher.exists() && folderFetcher.mkdir()) {
            System.out.println("Folder created");
        }

        // Set up ChromeOptions
        System.setProperty("webdriver.chrome.driver", "src/main/java/chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");

        StockDataFetcher fetcher = new StockDataFetcher();
        List<String> dropdownValues = DropdownExtractor.getDropdownValues();

        // Initialize WebDriver pool with six instances
        List<WebDriver> drivers = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            drivers.add(new ChromeDriver(options));
        }

        // Create a thread pool for six threads
        ExecutorService executor = Executors.newFixedThreadPool(6);

        // Check for last saved date
        String lastDate = fetcher.getLastSavedDate();

        // Format current date for fetching date range
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        LocalDate today = LocalDate.now();
        String toDateStr = today.format(formatter);

        if(lastDate.equals(toDateStr)) {
            System.out.println("ALL DATA IS FETCHED to " + toDateStr + " date." );
            System.out.println("Total execution time: " + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds.");

            return ;
        }

        if (!lastDate.isEmpty()) {
            // Iterate over symbols and submit tasks to the executor
            for (String symbol : dropdownValues) {
                counter++;
                executor.submit(() -> {
                    WebDriver driver = null;
                    try {
                        synchronized (drivers) {
                            // Retrieve a WebDriver from the pool
                            while (drivers.isEmpty()) {
                                drivers.wait();
                            }
                            driver = drivers.remove(0);
                        }
                        fetcher.fetchHistoricalData(symbol, lastDate, toDateStr, driver);

                        System.out.println("Up to date symbol: " + symbol);

                    } catch (Exception e) {
                        System.out.println("Error fetching data for " + symbol + ": " + e.getMessage());
                    } finally {
                        if (driver != null) {
                            synchronized (drivers) {
                                drivers.add(driver); // Return driver to pool
                                drivers.notify();
                            }
                        }
                    }
                });
            }
        }else {
            // Iterate over symbols and submit tasks to the executor
            for (String symbol : dropdownValues) {
                counter++;
                executor.submit(() -> {
                    WebDriver driver = null;
                    try {
                        synchronized (drivers) {
                            // Retrieve a WebDriver from the pool
                            while (drivers.isEmpty()) {
                                drivers.wait();
                            }
                            driver = drivers.remove(0);
                        }
                        fetcher.fetchHistoricalDataTenYears(symbol, driver);

                        System.out.println("11years(01.01.2014-" + toDateStr +") of data for symbol: " + symbol);

                    } catch (Exception e) {
                        System.out.println("Error fetching data for " + symbol + ": " + e.getMessage());
                    } finally {
                        if (driver != null) {
                            synchronized (drivers) {
                                drivers.add(driver); // Return driver to pool
                                drivers.notify();
                            }
                        }
                    }
                });
            }
        }

        // Shut down the executor service gracefully
        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

        //zacuvaja novata data;ako gi pominish nad 160 kodoj
        if(counter>160) {
            fetcher.updateLastSavedDate();
        }

        //isklucigi site chrome
        for (WebDriver driver : drivers) {
            driver.quit();
        }

        System.out.println("Total execution time: " + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds.\nMinutes: " + ((System.currentTimeMillis() - startTime) / 1000.0)/60.0);
    }

}