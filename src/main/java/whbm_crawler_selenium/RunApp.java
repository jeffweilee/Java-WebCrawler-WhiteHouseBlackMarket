package whbm_crawler_selenium;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.sql.Timestamp;
import java.util.Date;

import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.csvreader.CsvWriter;

public class RunApp {
	// public static File
	public static File driverFile;
	public static ChromeDriver chromeDriver;
	public static WebDriverWait wait;
	public static String csvFile = "whbm.csv";
	public static String logFile = "whbm.log";

	// original data
	public static List<String> productLink = new ArrayList<>();
	public static List<String> productStyleid = new ArrayList<>();

	// storage data
	public static List<String> styleid = new ArrayList<>();
	public static List<String> name = new ArrayList<>();
	public static List<String> rating = new ArrayList<>();
	public static List<String> ratingCount = new ArrayList<>();
	public static List<String> priceNow = new ArrayList<>();
	public static List<String> priceWas = new ArrayList<>();
	public static List<String> size = new ArrayList<>();
	public static List<String> inventory = new ArrayList<>();
	public static Select sizeSelector;
	public static Select qtySelector;

	public static Logger logger;
	public static FileHandler fh;

	public static void main(String[] args) {
		try {
			logger = Logger.getLogger("whbmLog");
			fh = new FileHandler(logFile);
			logger.addHandler(fh);
			SimpleFormatter formatter = new SimpleFormatter();
			fh.setFormatter(formatter);

			setUp();
			clearVar();
			getProductList();
			getDetail();
			writeCSV();
			if (chromeDriver != null)
				chromeDriver.quit();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void setUp() {
		try {
			String OS = System.getProperty("os.name").toLowerCase();

			if (chromeDriver != null)
				chromeDriver.quit();
			if (OS.indexOf("win") >= 0) {
				driverFile = new File("driver/chromedriver_win.exe");
			} else if (OS.indexOf("mac") >= 0) {
				driverFile = new File("driver/chromedriver_mac");
			}
			System.setProperty("webdriver.chrome.driver", driverFile.getAbsolutePath());
			chromeDriver = new ChromeDriver();
			wait = new WebDriverWait(chromeDriver, 20);
			setPage();

		} catch (Exception e) {
			if (e.toString().contains("not exist")) {
				writeLog(e.getMessage());
			}
			setUp();
		}
	}

	public static void setPage() {
		chromeDriver.manage().deleteAllCookies();
		chromeDriver.get("https://www.whitehouseblackmarket.com/store/?wm=T");
		clearPopup();
		changeCurrency("USD");
	}

	public static void processException(Exception e, String from, int index) {
		String msg = from + "\n";
		if (index != -1)
			msg += "item " + index + ": id(" + productStyleid.get(index) + ")";
		writeLog(msg);
		if (e.toString().contains("no such session") || e.toString().contains("chrome not reachable")
				|| e.toString().contains("no such window") || e.toString().contains("IllegalMonitorState")
				|| e.toString().contains("crash") || e.toString().contains("died")
				|| e.toString().contains("renderer")) {
			setUp();
		} else if (index != -1 && chromeDriver.getCurrentUrl() != productLink.get(index)) {
			writeLog("Redirect Page " + index + ": " + productLink.get(index));
			chromeDriver.get(productLink.get(index));
			wait.until(ExpectedConditions
					.presenceOfAllElementsLocatedBy(By.cssSelector("#product-options > div:nth-child(2) > select")));
			wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("#skuQty1")));
			sizeSelector = new Select(
					chromeDriver.findElement(By.cssSelector("#product-options > div:nth-child(2) > select")));
			qtySelector = new Select(chromeDriver.findElement(By.cssSelector("#skuQty1")));
		} else {
			clearPopup();
			clearAlert();
		}
	}

	public static void getProductList() {

		List<String> productList = new ArrayList<>();
		productList.add("http://www.whitehouseblackmarket.com/store/product-list/?No=0&Nrpp=1");
		// productList.add("http://www.whitehouseblackmarket.com/store/product-list/?No=1000&Nrpp=1");

		for (int i = 0; i < productList.size(); i++) {
			try {
				String url = productList.get(i);
				chromeDriver.get(url);
				List<WebElement> link = chromeDriver.findElements(By.xpath("//div[@class='product-information']/a[1]"));
				List<WebElement> id = chromeDriver.findElements(By.xpath("//div[@class='product-information']/a[1]"));

				if (link.size() > 0) {
					link.forEach(item -> {
						if (!item.getAttribute("href").contains("EGIFTCERTWH"))
							productLink.add(item.getAttribute("href"));
					});
					id.forEach(item -> {
						if (!item.getAttribute("href").contains("EGIFTCERTWH"))
							productStyleid
									.add(item.getAttribute("onclick").replaceAll("[s_objectID='product__name;]", ""));
					});
				} else {
					clearVar();
					setUp();
					i = -1;
				}
			} catch (Exception e) {
				processException(e, "getProductList", -1);
				clearVar();
				i = -1;
			}
		}
		writeLog("Number of products: " + productLink.size());
	}

	private static void getDetail() {
		for (int i = 0; i < productLink.size(); i++) {
			try {
				// clear cache and sleep
				if (i > 0 && i % 100 == 0) {
					Thread.sleep(10000);
					setUp();
				}

				chromeDriver.get(productLink.get(i));
				List<WebElement> id = chromeDriver.findElements(By.xpath("//*[@id='product-style']/span/span[2]"));
				List<WebElement> productName = chromeDriver.findElements(By.cssSelector("#product-name"));
				List<WebElement> regularPrice = chromeDriver.findElements(By.cssSelector(
						"#frmAddToBag > div.fieldset-wrapper > fieldset.product-fieldset.fieldset0 > div.product-price-wrapper > div > span.regular-price"));
				List<WebElement> salesPrice = chromeDriver.findElements(By.cssSelector(
						"#frmAddToBag > div.fieldset-wrapper > fieldset.product-fieldset.fieldset0 > div.product-price-wrapper > div > span.sale-price"));
				List<WebElement> BVRRRatingNumber = chromeDriver
						.findElements(By.xpath("//*[@id='BVRRRatingOverall_Rating_Summary_1']/div[3]/span[1]"));
				List<WebElement> BVRRReviewCount = chromeDriver
						.findElements(By.xpath("//*[@id='BVRRRatingSummaryLinkReadID']/a/span/span"));
				if (!id.isEmpty()) {
					styleid.add(id.get(0).getText());
					name.add(productName.get(0).getText());
					priceNow.add(regularPrice.get(0).getText().replaceAll("[^\\d+\\.?[\\,]\\d*$]", "")
							.replaceAll("\\s", "").replace("$", ""));
					priceWas.add(salesPrice.get(0).getText().replaceAll("[^\\d+\\.?[\\,]\\d*$]", "")
							.replaceAll("\\s", "").replace("$", ""));
					rating.add(BVRRRatingNumber.get(0).getText());
					ratingCount.add(BVRRReviewCount.get(0).getText());
					getInventory(i);
				} else {
					writeLog("getDetail: page fnd no content");
					i--;
				}
			} catch (Exception e) {
				processException(e, "getDetail", i);
				i--;
			}
		}
	}

	public static void getInventory(int i) {
		try {
			List<WebElement> sizeSelectorList = chromeDriver
					.findElements(By.cssSelector("#product-options > div:nth-child(2) > select"));
			List<WebElement> qtySelectorList = chromeDriver.findElements(By.cssSelector("#skuQty1"));

			if (!sizeSelectorList.isEmpty() && !qtySelectorList.isEmpty() && sizeSelectorList.get(0).isDisplayed()
					&& qtySelectorList.get(0).isDisplayed()) {
				fetchClothesLikeProduct(i, sizeSelectorList, qtySelectorList);
			} else if (!qtySelectorList.isEmpty() && qtySelectorList.get(0).isDisplayed()
					&& !sizeSelectorList.get(0).isDisplayed()) {
				fetchGadgetLikeProduct(i, qtySelectorList);
			} else { /*** out of stock ***/
				inventory.add("0");
				size.add("NA");
			}
		} catch (Exception e) {
			processException(e, "getInventory", i);
		}
	}

	public static void fetchClothesLikeProduct(int i, List<WebElement> sizeSelectorList,
			List<WebElement> qtySelectorList) {
		sizeSelector = new Select(sizeSelectorList.get(0));
		qtySelector = new Select(qtySelectorList.get(0));

		// fetch size list
		List<String> sizeList = new ArrayList<String>();
		sizeSelector.getOptions().forEach(item -> {
			sizeList.add(item.getText());
		});
		if (sizeList.get(0).contains("Select"))
			sizeList.remove(0);

		// get inventory of each size
		List<String> inventoryNow = new ArrayList<String>();
		List<String> sizeNow = new ArrayList<String>();

		for (int j = 0; j < sizeList.size(); j++) {
			String s = sizeList.get(j);

			// refresh page to get correct inventory
			if (j > 0) {
				chromeDriver.navigate().refresh();
				wait.until(ExpectedConditions
						.presenceOfElementLocated(By.cssSelector("#product-options > div:nth-child(2) > select")));
				wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#skuQty1")));
				sizeSelector = new Select(
						chromeDriver.findElement(By.cssSelector("#product-options > div:nth-child(2) > select")));
				qtySelector = new Select(chromeDriver.findElement(By.cssSelector("#skuQty1")));
			}

			// select size and quantity
			sizeSelector.selectByVisibleText(s);
			qtySelector.selectByVisibleText("20");

			// get response
			String submitResult = submitBag(i);
			if (submitResult == "timeout") {
				writeLog("timeout item: id(" + productStyleid.get(i) + ") size(" + sizeList.get(j) + ")");
				j--;
			} else {
				inventoryNow.add(submitResult);
				sizeNow.add(s);
			}
		}

		inventoryNow.forEach(item -> {
			inventory.add(item);
		});
		sizeNow.forEach(item -> {
			size.add(item);
		});
	}

	public static void fetchGadgetLikeProduct(int i, List<WebElement> qtySelectorList) {
		// just select quantity, no size
		qtySelector = new Select(qtySelectorList.get(0));
		qtySelector.selectByVisibleText("20");

		for (int j = 0; j < 1; j++) {
			String submitResult = submitBag(i);
			if (submitResult == "timeout") {
				writeLog("timeout item: id(" + productStyleid.get(i) + ") size(NA)");
				j--;
			} else {
				inventory.add(submitResult);
				size.add("NA");
			}
		}
	}

	public static String submitBag(int index) {
		String left = "20+";
		try {
			WebElement submitBagBtn = chromeDriver.findElement(By.cssSelector("#add-to-bag"));
			wait.until(ExpectedConditions.elementToBeClickable(submitBagBtn));
			Actions actions = new Actions(chromeDriver);
			actions.moveToElement(submitBagBtn).click().perform();
			// wait.until(ExpectedConditions.invisibilityOfElementLocated(By.className("modalWindow")));
			wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#pc-overflow")));

			// check if exists inventory message
			Pattern selloutPtn = Pattern.compile("Only \\((?<leftAmt>\\d+)\\) left");
			List<WebElement> sellOutDivs = chromeDriver.findElements(By.cssSelector("#zone-error"));
			if (!sellOutDivs.isEmpty() && sellOutDivs.get(0).isDisplayed()) {
				String selloutText = sellOutDivs.get(0).getText();
				Matcher matcher = selloutPtn.matcher(selloutText);
				if (matcher.find()) {
					left = matcher.group("leftAmt");
				}
			}
			// Thread.sleep(1500);
		} catch (Exception e) {
			processException(e, "submitBag", index);
			return "timeout";
		}
		return left;
	}

	public static List<Map<String, String>> processPrice(String p) {
		String priceNow = "NA";
		String priceWas = "NA";
		String price = "NA";
		List<Map<String, String>> priceList = new ArrayList<>();
		Map<String, String> pm = new HashMap<String, String>();
		try {
			if (p.contains("was")) {
				priceNow = p.split("was")[0].replaceAll("[^\\d+\\.?[\\,]\\d*$]", "").replaceAll("\\s", "").replace("$",
						"");
				priceWas = p.split("was")[1].replaceAll("[^\\d+\\.?[\\,]\\d*$]", "").replaceAll("\\s", "").replace("$",
						"");
			} else {
				price = p.replaceAll("[^\\d+\\.?[\\,]\\d*$]", "").replaceAll("\\s", "").replace("$", "");
			}
			pm.put("priceNow", priceNow);
			pm.put("priceWas", priceWas);
			pm.put("price", price);

		} catch (Exception e) {
			e.printStackTrace();
		}
		priceList.add(pm);
		return priceList;
	}

	public static void changeCurrency(String currency) {
		Select optionSelector = new Select(
				chromeDriver.findElement(By.cssSelector("#context-selector-currency-select")));
		optionSelector.selectByValue(currency);

		WebElement submitBtn = chromeDriver.findElement(By.cssSelector(
				"#localizationPrefSave > div.context-selector-call-to-action > input[type='image']:nth-child(5)"));
		wait.until(ExpectedConditions.elementToBeClickable(submitBtn));
		Actions actions_submitBtn = new Actions(chromeDriver);
		actions_submitBtn.moveToElement(submitBtn).click().perform();
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.className("modalWindow")));
	}

	public static void clearPopup() {
		List<WebElement> closePopupBtn_shippment = chromeDriver.findElements(By.cssSelector("#closeButton"));
		if (!closePopupBtn_shippment.isEmpty()) {
			wait.until(ExpectedConditions.elementToBeClickable(closePopupBtn_shippment.get(0)));
			Actions action_closePopupBtn_shippment = new Actions(chromeDriver);
			action_closePopupBtn_shippment.moveToElement(closePopupBtn_shippment.get(0)).click().perform();
		}

		List<WebElement> closePopupBtn_giftCard = chromeDriver
				.findElements(By.cssSelector("#jModal > table > tbody > tr.modalControls > td:nth-child(2) > a"));
		if (!closePopupBtn_giftCard.isEmpty()) {
			Actions action_closePopupBtn_giftCard = new Actions(chromeDriver);
			action_closePopupBtn_giftCard.moveToElement(closePopupBtn_giftCard.get(0)).click().perform();
		}
	}

	public static void clearAlert() {
		try {
			Alert alert = new WebDriverWait(chromeDriver, 1).until(ExpectedConditions.alertIsPresent());
			if (alert != null) {
				writeLog("Alert is present");
				alert.accept();
			}
		} catch (Exception e) {
			// e.printStackTrace();
		}
	}

	public static void clearVar() {
		productStyleid.clear();
		productLink.clear();
		styleid.clear();
		name.clear();
		rating.clear();
		ratingCount.clear();
		priceNow.clear();
		priceWas.clear();
		size.clear();
		inventory.clear();
	}

	public static void writeCSV() {
		String timeNow = new Timestamp(new Date().getTime()).toString();
		boolean alreadyExists = new File(csvFile).exists();
		try {
			CsvWriter csvOutput = new CsvWriter(new FileWriter(csvFile, true), ',');

			// write header
			if (!alreadyExists) {
				csvOutput.writeRecord(new String[] { "timeStamp", "styleid", "name", "rating", "ratingCount", "price",
						"priceNow", "priceWas", "size", "inventory" });
			}

			// write records
			for (int i = 0; i < styleid.size(); i++) {
				csvOutput.write(timeNow);
				csvOutput.write(styleid.get(i));
				csvOutput.write(name.get(i));
				csvOutput.write(rating.get(i));
				csvOutput.write(ratingCount.get(i));
				csvOutput.write(priceNow.get(i));
				csvOutput.write(priceWas.get(i));
				csvOutput.write(size.get(i));
				csvOutput.write(inventory.get(i));
				csvOutput.endRecord();
			}
			writeLog(timeNow + " successfully write " + styleid.size() + " products (" + styleid.size()
					+ " records) to " + csvFile + "\n\n");
			csvOutput.close();
		} catch (IOException e) {
			// e.printStackTrace();
			writeLog("csvFile in use. Save to " + "whbm_" + timeNow + ".csv");
			writeCSVTemp("whbm_" + timeNow + ".csv", timeNow);
		}
	}

	public static void writeCSVTemp(String file, String timeNow) {

		file = file.replaceAll("\\s", "_").replaceAll("[ :-]", "_");
		boolean alreadyExists = new File(file).exists();
		try {
			CsvWriter csvOutput = new CsvWriter(new FileWriter(file, true), ',');

			// write header
			if (!alreadyExists) {
				csvOutput.writeRecord(new String[] { "timeStamp", "styleid", "name", "rating", "ratingCount", "price",
						"priceNow", "priceWas", "size", "inventory" });
			}
			if (inventory.size() != styleid.size()) {
				System.out.println(
						timeNow + "Size mismatched: inventory(" + inventory.size() + ") id(" + styleid.size() + ")");
			}
			// write records
			for (int i = 0; i < inventory.size(); i++) {
				csvOutput.write(timeNow);
				csvOutput.write(styleid.get(i));
				csvOutput.write(name.get(i));
				csvOutput.write(rating.get(i));
				csvOutput.write(ratingCount.get(i));
				csvOutput.write(priceNow.get(i));
				csvOutput.write(priceWas.get(i));
				csvOutput.write(size.get(i));
				csvOutput.write(inventory.get(i));
				csvOutput.endRecord();
			}
			writeLog(timeNow + " successfully write temp " + styleid.size() + " products (" + styleid.size()
					+ " records) to " + csvFile + "\n\n");
			csvOutput.close();
		} catch (IOException e) {
			writeLog(e.getMessage());
		}
	}

	private static void writeLog(String msg) {
		// System.out.println("\n\n " + new Timestamp(new
		// Date().getTime()).toString() + " - " + msg);
		try {
			logger.info(msg);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
