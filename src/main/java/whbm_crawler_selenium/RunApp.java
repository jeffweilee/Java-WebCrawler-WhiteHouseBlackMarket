package whbm_crawler_selenium;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
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
	public static String timeNow = "";

	// original data
	public static List<String> productLink = new ArrayList<>();

	// storage data
	public static List<String> styleid = new ArrayList<>();
	public static List<String> name = new ArrayList<>();
	public static List<String> rating = new ArrayList<>();
	public static List<String> reviewCount = new ArrayList<>();
	public static List<String> priceNow = new ArrayList<>();
	public static List<String> priceWas = new ArrayList<>();
	public static List<String> size = new ArrayList<>();
	public static List<String> inventory = new ArrayList<>();
	public static Select sizeSelector;
	public static Select qtySelector;

	public static Logger logger;
	public static FileHandler fh;

	public static void main(String[] args) throws InterruptedException {
		try {
			// log
			logger = Logger.getLogger("whbmLog");
			logger.setUseParentHandlers(false);
			fh = new FileHandler(logFile, true);
			logger.addHandler(fh);
			SimpleFormatter formatter = new SimpleFormatter();
			fh.setFormatter(formatter);
			// start
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
			setDriver(OS);
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

	public static void setDriver(String OS) {
		if (chromeDriver != null)
			chromeDriver.quit();
		if (OS.indexOf("win") >= 0) {
			driverFile = new File("driver/chromedriver_win.exe");
		} else if (OS.indexOf("mac") >= 0) {
			driverFile = new File("driver/chromedriver_mac");
		}

		if (driverFile.exists()) {
			System.setProperty("webdriver.chrome.driver", driverFile.getAbsolutePath());
		} else {
			writeLog("ChromeDriver not found! Origin path: " + driverFile.getPath());
		}

		chromeDriver = new ChromeDriver();
		wait = new WebDriverWait(chromeDriver, 30);
	}

	public static void processException(Exception e, String from, int index) {
		String msg = from + ": ";
		if (index != -1 && productLink.size() > index) {
			if (from != "GetProductList")
				msg += "Item " + index + " [" + productLink.get(index) + "]";
			else if (from != "GetProductList")
				msg += "Catgory " + index + ": " + " [" + chromeDriver.getCurrentUrl() + "]";
		}
		writeLog(msg);
		e.printStackTrace();

		try {
			String err = e.toString();
			if (err.contains("no such session") || err.contains("chrome not reachable")
					|| err.contains("no such window") || err.contains("IllegalMonitorState") || err.contains("crash")
					|| err.contains("died") || err.contains("renderer") || err.contains("RetryExec")) {
				setUp();
			} else if (e.toString().contains("alert")) {
				clearAlert();
			} else if (index != -1 && chromeDriver.getCurrentUrl() != productLink.get(index)
					|| e.toString().contains("locate element")) {
				writeLog("Redirect Page " + index + ": " + productLink.get(index));
				if (from == "FetchClothesLikeProduct" || from.toLowerCase() == "FetchGadgetLikeProduct") {
					chromeDriver.get(productLink.get(index));
					wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(
							By.cssSelector("#product-options > div:nth-child(2) > select")));
					wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("#skuQty1")));
					sizeSelector = new Select(
							chromeDriver.findElement(By.cssSelector("#product-options > div:nth-child(2) > select")));
					qtySelector = new Select(chromeDriver.findElement(By.cssSelector("#skuQty1")));
				}
			} else {
				clearPopup();
			}
		} catch (Exception e1) {
			setUp();
		}
	}

	public static void getProductList() {
		List<String> productList = new ArrayList<>();
		// sale
		productList.add("https://www.whitehouseblackmarket.com/store/sale/catsales");
		// accessory
		productList.add("https://www.whitehouseblackmarket.com/store/category/jewelry-accessories/cat210019");
		// jeans
		productList.add("https://www.whitehouseblackmarket.com/store/category/all-jeans/cat210023");
		// jackets
		productList.add("https://www.whitehouseblackmarket.com/store/category/jackets-vests/cat210004");
		// tops
		productList.add("https://www.whitehouseblackmarket.com/store/category/tops/cat210001");
		// skirts
		productList.add("https://www.whitehouseblackmarket.com/store/category/dresses-skirts/cat210002");
		// petities
		productList.add("https://www.whitehouseblackmarket.com/store/category/petites/cat8739284");
		// work
		productList.add("https://www.whitehouseblackmarket.com/store/category/work/cat6219285");
		// new arrival
		productList.add("https://www.whitehouseblackmarket.com/store/category/new-arrivals/cat210006");

		writeLog("Start crawling...");
		String msg = "[";

		for (int i = 0; i < productList.size(); i++) {
			try {
				chromeDriver.get(productList.get(i));
				wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("#product-item-count")));
				int NumOfProdNow = Integer.parseInt(chromeDriver.findElement(By.cssSelector("#product-item-count"))
						.getText().replaceAll("\\D", ""));
				while (true) {
					((JavascriptExecutor) chromeDriver).executeScript("window.scrollTo(0,document.body.scrollHeight);");
					if (!chromeDriver.findElements(By.cssSelector("#pc" + NumOfProdNow)).isEmpty())
						break;
				}
				List<WebElement> link = chromeDriver.findElements(By.cssSelector(".product-name"));
				link.forEach(item -> {
					productLink.add(item.getAttribute("href"));
				});
				msg += link.size() + ", ";
			} catch (Exception e) {
				processException(e, "GetProductList", i);
				writeLog("GetProductList: " + productList.get(i));
				i--;
			}
		}
		Set<String> hs = new HashSet<>();
		hs.addAll(productLink);
		productLink.clear();
		productLink.addAll(hs);
		hs.clear();

		try {
			writeLog("# of Products: [sale, accessory, jeans, jackets, tops, skirts, petty, work, new arrival]: "
					+ msg.substring(0, msg.length() - 2) + "]");
			writeLog("# of Total Product:" + productLink.size());
		} catch (Exception e) {
			processException(e, "GetProductList", -1);
			getProductList();
		}
	}

	private static void getDetail() {
		for (int i = 0; i < productLink.size(); i++) {
			try {
				chromeDriver.get(productLink.get(i));

				checkCurrency("USD");
				List<WebElement> id = chromeDriver.findElements(By.xpath("//*[@class='style-id-number']"));
				List<WebElement> productName = chromeDriver.findElements(By.cssSelector("#product-name"));
				List<WebElement> regularPrice = chromeDriver.findElements(By.cssSelector(
						"#frmAddToBag > div.fieldset-wrapper > fieldset.product-fieldset.fieldset0 > div.product-price-wrapper > div > span.regular-price"));
				List<WebElement> salesPrice = chromeDriver.findElements(By.cssSelector(
						"#frmAddToBag > div.fieldset-wrapper > fieldset.product-fieldset.fieldset0 > div.product-price-wrapper > div > span.sale-price"));
				List<WebElement> BVRRRatingNumber = chromeDriver
						.findElements(By.xpath("//*[@id='BVRRRatingOverall_Rating_Summary_1']/div[3]/span[1]"));
				List<WebElement> BVRRReviewCount = chromeDriver.findElements(By.xpath("//*[@id='tab_numReviews']"));
				Boolean isOutOfStock = false;

				if (!id.isEmpty() && !productName.isEmpty()) {

					isOutOfStock = regularPrice.isEmpty() && salesPrice.isEmpty() ? true : false;

					String idNow = id.get(0).getText();
					String nameNow = productName.get(0).getText();
					String rPrice = isOutOfStock == true ? "NA"
							: regularPrice.get(0).getText().replaceAll("[^\\d+\\.?[\\,]\\d*$]", "")
									.replaceAll("\\s", "").replace("$", "");
					String sPrice = isOutOfStock == true ? "NA"
							: salesPrice.get(0).getText().replaceAll("[^\\d+\\.?[\\,]\\d*$]", "").replaceAll("\\s", "")
									.replace("$", "");
					String ratingNow = BVRRRatingNumber.isEmpty() == true ? "NA" : BVRRRatingNumber.get(0).getText();
					String reviewCountNow = BVRRReviewCount.isEmpty() == true ? "NA"
							: BVRRReviewCount.get(0).getText().replaceAll("\\D", "");

					styleid.add(idNow);
					name.add(nameNow);
					priceNow.add(rPrice);
					priceWas.add(sPrice);
					rating.add(ratingNow);
					reviewCount.add(reviewCountNow);

					getInventory(i);
				} else {
					writeLog("GetDetail: page find no content or elements; " + productLink.get(i));
					i--;
				}
			} catch (Exception e) {
				processException(e, "GetDetail|GetInventory", i);
				i--;
			}
		}
	}

	public static Boolean getInventory(int i) {
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
			return true;
		}
		return false;
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
			try {
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
					writeLog("timeout item: id(" + productLink.get(i) + ") size(" + sizeList.get(j) + ")");
					j--;
				} else {
					inventoryNow.add(submitResult);
					sizeNow.add(s);
				}
			} catch (Exception e) {
				processException(e, "FetchClothesLikeProduct", i);
				j--;
			}
		}

		inventoryNow.forEach(item -> {
			inventory.add(item);
		});
		sizeNow.forEach(item -> {
			size.add(item);
		});

		// replicate the record for number of size times
		for (int j = 0; j < sizeList.size() - 1; j++) {
			styleid.add(styleid.get(styleid.size() - 1));
			name.add(name.get(name.size() - 1));
			rating.add(rating.get(rating.size() - 1));
			reviewCount.add(reviewCount.get(reviewCount.size() - 1));
			priceNow.add(priceNow.get(priceNow.size() - 1));
			priceWas.add(priceWas.get(priceWas.size() - 1));
		}

	}

	public static void fetchGadgetLikeProduct(int i, List<WebElement> qtySelectorList) {
		// just select quantity, no size
		qtySelector = new Select(qtySelectorList.get(0));
		qtySelector.selectByVisibleText("20");

		for (int j = 0; j < 1; j++) {
			try {
				String submitResult = submitBag(i);
				if (submitResult == "timeout") {
					writeLog("timeout item: id(" + productLink.get(i) + ") size(NA)");
					j--;
				} else {
					inventory.add(submitResult);
					size.add("NA");
				}
			} catch (Exception e) {
				processException(e, "FetchGadgetLikeProduct", i);
				j--;
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
			processException(e, "SubmitBag", index);
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
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".jqmID13")));
	}

	public static void checkCurrency(String currency) {
		List<WebElement> reg = chromeDriver.findElements(By.cssSelector(
				"#frmAddToBag > div.fieldset-wrapper > fieldset.product-fieldset.fieldset0 > div.product-price-wrapper > div > span.regular-price"));
		List<WebElement> sal = chromeDriver.findElements(By.cssSelector(
				"#frmAddToBag > div.fieldset-wrapper > fieldset.product-fieldset.fieldset0 > div.product-price-wrapper > div > span.sale-price"));
		Boolean flag = false;
		if (!reg.isEmpty()) {
			if (reg.get(0).getText().contains("TWD")) {
				flag = true;
			}
		} else if (!sal.isEmpty()) {
			if (reg.get(0).getText().contains("TWD")) {
				flag = true;
			}
		}
		if (flag) {
			clearPopup();
			WebElement countryBtn = chromeDriver.findElement(By.cssSelector("#ibf-countryName"));
			Actions actions_submitBtn = new Actions(chromeDriver);
			wait.until(ExpectedConditions.elementToBeClickable(countryBtn));
			actions_submitBtn.moveToElement(countryBtn).click().perform();
			changeCurrency(currency);
		}
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
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector("#tinymask")));
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
		productLink.clear();
		styleid.clear();
		name.clear();
		rating.clear();
		reviewCount.clear();
		priceNow.clear();
		priceWas.clear();
		size.clear();
		inventory.clear();
	}

	public static void writeCSV() {
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
				csvOutput.write(reviewCount.get(i));
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
				csvOutput.write(reviewCount.get(i));
				csvOutput.write(priceNow.get(i));
				csvOutput.write(priceWas.get(i));
				csvOutput.write(size.get(i));
				csvOutput.write(inventory.get(i));
				csvOutput.endRecord();
			}
			writeLog("Successfully write temp " + productLink.size() + " products (" + styleid.size() + " records) to "
					+ csvFile);
			csvOutput.close();
		} catch (IOException e) {
			writeLog(e.getMessage());
		}
	}

	private static void writeLog(String msg) {
		// System.out.println("\n\n " + new Timestamp(new
		// Date().getTime()).toString() + " - " + msg);
		try {
			logger.info("======" + msg + "======");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
