package whbm_crawler_selenium;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
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
	public static String csvFileIndi = "";
	public static String csvFile_backup = "whbm_backup.csv";
	public static String logFile = "whbm.log";
	public static String newline = "\n";
	public static String seperator = "\n====================";
	public static String timeNow = "";

	// original data
	public static List<String> productLink = new ArrayList<>();

	// storage data
	public static List<String> styleid = new ArrayList<>();
	public static List<String> name = new ArrayList<>();
	public static List<String> rating = new ArrayList<>();
	public static List<String> reviewCount = new ArrayList<>();
	public static List<String> regularPrice = new ArrayList<>();
	public static List<String> salesPrice = new ArrayList<>();
	public static List<String> size = new ArrayList<>();
	public static List<String> inventory = new ArrayList<>();
	public static Select sizeSelector;
	public static Select qtySelector;
	public static String SKIP = "skip";
	public static Logger logger;
	public static FileHandler fh;

	RunApp() {
		csvFileIndi = "";
	};

	public static void main(String[] args) throws InterruptedException {
		try {
			// log
			logger = Logger.getLogger("whbmLog");
			logger.setUseParentHandlers(false);
			fh = new FileHandler(logFile, true);
			Locale.setDefault(Locale.ENGLISH);
			SimpleFormatter formatter = new SimpleFormatter();
			fh.setFormatter(formatter);
			logger.addHandler(fh);
			timeNow = LocalDateTime.now().toString();
			csvFileIndi = "whbm" + timeNow + ".csv";
			// start
			setUp();
			clearVar();
			getProductList();
			getDetail();
			writeCSV(-1, -1);
			if (chromeDriver != null)
				chromeDriver.quit();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void setUp() {
		try {
			if (chromeDriver != null)
				chromeDriver.quit();
			String OS = System.getProperty("os.name").toLowerCase();
			setDriver(OS);
			setPage();
		} catch (Exception e) {
			if (e.toString().contains("not exist")) {
				logger.info(e.getMessage() + seperator);
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
			logger.info("ChromeDriver not found! Origin path: " + driverFile.getPath() + seperator);
		}

		chromeDriver = new ChromeDriver();
		wait = new WebDriverWait(chromeDriver, 20);
	}

	public static int indexInProcessExcpetion = 0;
	public static int countInProcessExcpetion = 0;

	public static String processException(Exception e, String from, int index) {
		e.printStackTrace();
		String err = e.toString();
		System.out.println(err);
		String msg = from + ": ";

		if (index != indexInProcessExcpetion) {
			indexInProcessExcpetion = index;
			countInProcessExcpetion = 0;
		} else {
			++countInProcessExcpetion;
			if (countInProcessExcpetion > 1) {
				setUp();
				if (err.contains("NoSuchElementException"))
					return SKIP;
			}
		}

		try {
			if (index != -1 && productLink.size() > index) {
				if (from == "GetProductList")
					msg += "Category " + index + ": " + " [" + chromeDriver.getCurrentUrl() + "]";
				else
					msg += "Item " + index + "\n[" + productLink.get(index) + "]";
			}
			if (err.contains("UnreachableBrowserException") || err.contains("SessionNotFoundException")
					|| err.contains("UnhandledAlertException") || err.contains("no such session")
					|| err.contains("chrome not reachable") || err.contains("no such window") || err.contains("crash")
					|| err.contains("died") || err.contains("renderer") || err.contains("SessionNotFoundException")
					|| err.contains("IllegalMonitorState") || err.contains("RetryExec")) {
				logger.info(msg + newline);
				logger.info(err + seperator);
				setUp();
			} else if (err.contains("NoSuchElementException")) {
				logger.info(msg + newline);
				logger.info(err + seperator);
				clearAlert();
			} else if (err.contains("alert")) {
				logger.info(msg + newline);
				logger.info(err + seperator);
				clearAlert();
			} else if (index != -1 && !chromeDriver.getCurrentUrl().equals(productLink.get(index))
					|| err.contains("TimeoutException")) {
				if (from.equals("SubmitBag")) {
					logger.info("Redirect Page " + index + ": " + productLink.get(index) + " because ");
					chromeDriver.get(productLink.get(index));
				} else {
					logger.info(msg + newline);
					logger.info(err + newline);
					logger.info("Redirect Page " + index + ": " + productLink.get(index) + seperator);
					chromeDriver.get(productLink.get(index));
				}
				if (from == "GetInventory" || from == "FetchClothesLikeProduct" || from == "FetchGadgetLikeProduct") {
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
		} catch (Exception e1)

		{
			setUp();
		}
		return null;

	}

	public static void getProductList() {
		logger.info("Start crawling..." + seperator);
		List<String> productList = new ArrayList<>();
		productList.add("https://www.whitehouseblackmarket.com/store/sale/catsales");
//		productList.add("https://www.whitehouseblackmarket.com/store/category/jewelry-accessories/cat210019");
//		productList.add("https://www.whitehouseblackmarket.com/store/category/all-jeans/cat210023");
//		productList.add("https://www.whitehouseblackmarket.com/store/category/jackets-vests/cat210004");
//		productList.add("https://www.whitehouseblackmarket.com/store/category/tops/cat210001");
//		productList.add("https://www.whitehouseblackmarket.com/store/category/dresses-skirts/cat210002");
//		productList.add("https://www.whitehouseblackmarket.com/store/category/petites/cat8739284");
//		productList.add("https://www.whitehouseblackmarket.com/store/category/work/cat6219285");
//		productList.add("https://www.whitehouseblackmarket.com/store/category/new-arrivals/cat210006");

		String msg = "[";
		for (int i = 0; i < productList.size(); i++) {
			try {
				chromeDriver.get(productList.get(i));
				wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("#product-item-count")));
				int NumOfProdNow = Integer.parseInt(chromeDriver.findElement(By.cssSelector("#product-item-count"))
						.getText().replaceAll("\\D", ""));
				while (true) {
					if (!chromeDriver.getCurrentUrl().equals(productList.get(i))) {
						chromeDriver.get(productList.get(i));
					}
					((JavascriptExecutor) chromeDriver).executeScript("window.scrollTo(0,document.body.scrollHeight);");
					if (!chromeDriver.findElements(By.cssSelector("#pc" + NumOfProdNow)).isEmpty())
						break;
				}
				List<WebElement> link = chromeDriver.findElements(By.cssSelector(".product-name"));
				link.forEach(item -> {
					productLink.add(item.getAttribute("href").trim().replaceAll(" *", ""));
				});
				msg += link.size() + ", ";
			} catch (Exception e) {
				if(processException(e, "GetProductList", i)!=SKIP)
					i--;
				logger.info("GetProductList: " + productList.get(i) + seperator);
			}
		}
		Set<String> hs = new HashSet<>();
		hs.addAll(productLink);
		productLink.clear();
		productLink.addAll(hs);
		hs.clear();

		try {
			logger.info("# of Items:" + newline
					+ "[sale, accessory, jeans, jackets, tops, skirts, petty, work, new arrival]: " + newline
					+ msg.trim().substring(0, msg.length() - 2) + "]" + seperator);
			logger.info("# of Total Items: " + productLink.size() + seperator);
		} catch (Exception e) {
			processException(e, "GetProductList", -1);
			getProductList();
		}
	}

	private static void getDetail() {
		logger.info("Start getDetail..." + seperator);
		Boolean flagRerun = false;
		int writeFreq = 2;
		int lastWriteIndex = 0;
		for (int i = 0; i <= productLink.size(); i++) {
			try {
				if ((i % writeFreq == 0 && i != 0 || i == productLink.size()) && !flagRerun) {
					writeCSV(lastWriteIndex, styleid.size());
					lastWriteIndex = styleid.size();
					String sMsg = (i == productLink.size() - 1) ? "" : String.valueOf(i - writeFreq);
					logger.info("No. " + sMsg + " - " + (i - 1) + " item wrtite to " + csvFile + " successfully!"
							+ seperator);
					setPage();
				}
				if (i % 100 == 1)
					logger.info("No. " + (i - 1) + " item completed!" + newline + " [" + productLink.get(i - 1) + "]"
							+ seperator);
				if (i < productLink.size()) {
					flagRerun = true;
					chromeDriver.get(productLink.get(i));
					checkCurrency("USD");
					List<WebElement> id = chromeDriver.findElements(By.xpath("//*[@class='style-id-number']"));
					List<WebElement> productName = chromeDriver.findElements(By.cssSelector("#product-name"));
					List<WebElement> regPrice = chromeDriver.findElements(By.cssSelector(
							"#frmAddToBag > div.fieldset-wrapper > fieldset.product-fieldset.fieldset0 > div.product-price-wrapper > div > span.regular-price"));
					List<WebElement> salPrice = chromeDriver.findElements(By.cssSelector(
							"#frmAddToBag > div.fieldset-wrapper > fieldset.product-fieldset.fieldset0 > div.product-price-wrapper > div > span.sale-price"));
					List<WebElement> BVRRRatingNumber = chromeDriver
							.findElements(By.xpath("//*[@id='BVRRRatingOverall_Rating_Summary_1']/span/span[1]"));
					List<WebElement> BVRRReviewCount = chromeDriver.findElements(By.xpath("//*[@id='tab_numReviews']"));
					Boolean isOutOfStock = false;
					if (!checkProductUrl(i))
						i--;
					else {
						if (!id.isEmpty() && !productName.isEmpty()) {
							flagRerun = false;
							isOutOfStock = regPrice.isEmpty() && salPrice.isEmpty() ? true : false;

							String idNow = id.get(0).getText();
							String nameNow = productName.get(0).getText();
							String rPrice = isOutOfStock == true ? "NA"
									: regPrice.get(0).getText().replaceAll("[^\\d+\\.?[\\,]\\d*$]", "")
											.replaceAll("\\s", "").replace("$", "");
							String sPrice = isOutOfStock == true || salPrice.get(0).getText().trim().isEmpty() ? "NA"
									: salPrice.get(0).getText().replaceAll("[^\\d+\\.?[\\,]\\d*$]", "")
											.replaceAll("\\s", "").replace("$", "");
							String ratingNow = BVRRRatingNumber.isEmpty() == true ? "NA"
									: BVRRRatingNumber.get(0).getText();
							String reviewCountNow = BVRRReviewCount.isEmpty() == true ? "NA"
									: BVRRReviewCount.get(0).getText().replaceAll("\\D", "");

							styleid.add(idNow);
							name.add(nameNow);
							regularPrice.add(rPrice);
							salesPrice.add(sPrice);
							rating.add(ratingNow);
							reviewCount.add(reviewCountNow);

							getInventory(i);
						} else {
							logger.info(
									"GetDetail: page find no content or elements; " + productLink.get(i) + seperator);
							flagRerun = true;
							i--;
						}
					}
				}
			} catch (Exception e) {
				if(processException(e, "GetDetail", i)!=SKIP)
					i--;
				flagRerun = true;
			}
		}
	}

	public static void getInventory(int i) {
		List<WebElement> sizeSelectorList = chromeDriver
				.findElements(By.cssSelector("#product-options > div:nth-child(2) > select"));
		List<WebElement> qtySelectorList = chromeDriver.findElements(By.cssSelector("#skuQty1"));
		if (!checkProductUrl(i))
			getInventory(i);
		else {
			if (!sizeSelectorList.isEmpty() && !qtySelectorList.isEmpty() && sizeSelectorList.get(0).isDisplayed()
					&& qtySelectorList.get(0).isDisplayed()) {
				fetchClothesLikeProduct(i);
			} else if (!qtySelectorList.isEmpty() && qtySelectorList.get(0).isDisplayed()
					&& !sizeSelectorList.get(0).isDisplayed()) {
				fetchGadgetLikeProduct(i);
			} else { /*** out of stock ***/
				inventory.add("0");
				size.add("NA");
			}
		}
	}
	
//	public static void getComment() {
//		List<WebElement> cmtRating = chromeDriver.findElements(By.cssSelector("#BVRRRatingOverall_Review_Display > div.BVRRRatingNormalOutOf > span.BVRRNumber.BVRRRatingNumber"));
//		List<WebElement> cmtName = chromeDriver.findElements(By.cssSelector("span.BVRRNickname"));
//		//all the nodes of profile is under this parent node div.BVRRContextDataContainer
//		//By.cssSelector("#BVSubmissionPopupContainer > div.BVRRReviewDisplayStyle3Summary > div.BVRRContextDataContainer")
//				
//		List<WebElement> cmtAge = chromeDriver.findElements(By.cssSelector("span.BVRRContextDataValueAge"));
//		List<WebElement> cmtBodyType = chromeDriver.findElements(By.cssSelector("span.BVRRContextDataValueBodyType"));
//		List<WebElement> cmtShoppingFrequency = chromeDriver
//				.findElements(By.cssSelector("span.BVRRContextDataValueShoppingFrequency"));
//		List<WebElement> cmtTitle = chromeDriver.findElements(By.cssSelector("span.BVRRReviewTitle"));
//		List<WebElement> cmtDate = chromeDriver.findElements(By.cssSelector("span.BVRRReviewDate"));
//		List<WebElement> cmtContent = chromeDriver.findElements(By.cssSelector("span.BVRRReviewText"));
//		List<WebElement> cmtHelpfulYes = chromeDriver.findElements(By.cssSelector("#BVSubmissionPopupContainer > div.BVRRReviewDisplayStyle3Main > div.BVDI_FV > div.BVDI_FVVoting.BVDI_FVVotingHelpfulness > div.BVDI_FVVotes.BVDI_FVVotesHelpfulness > span.BVDI_FVVote.BVDI_FVPositive > a > span > span.BVDINumber"));
//		List<WebElement> cmtHelpfulNo = chromeDriver.findElements(By.cssSelector("#BVSubmissionPopupContainer > div.BVRRReviewDisplayStyle3Main > div.BVDI_FV > div.BVDI_FVVoting.BVDI_FVVotingHelpfulness > div.BVDI_FVVotes.BVDI_FVVotesHelpfulness > span.BVDI_FVVote.BVDI_FVNegative > a > span > span.BVDINumber"));
//		
//		for(int i = 0 ; i<cmtRating.size(); i++){
//		String cmtRatingNow = cmtRating.get(i).getAttribute("innerText");
//		String cmtNameNow = cmtName.get(i).getText();
//		String cmtAgeNow = cmtAge.get(i).getText();
//		String cmtBodyTypeNow = cmtBodyType.get(i).getText();
//		String cmtShoppingFrequencyNow = cmtShoppingFrequency.get(0).getText();
//		String cmtTitleNow = "\""+cmtTitle.get(i).getText()+"\"";
//		String cmtDateNow = cmtDate.get(i).getText();
//		String cmtContentNow = "\""+cmtContent.get(i).getAttribute("innerText")+"\"";
//		String cmtYesNow= cmtHelpfulYes.get(i).getText();
//		String cmtNoNow= cmtHelpfulNo.get(i).getText();
//		System.out.println(cmtRatingNow + ", "+cmtNameNow+ ", "+cmtAgeNow+ ", "+cmtBodyTypeNow+ ", "+cmtShoppingFrequencyNow+ ", "+cmtTitleNow+ ", "+cmtDateNow+ ", "+cmtContentNow+", "+cmtYesNow+ ", "+cmtNoNow);
//		}
//	}

	public static void fetchClothesLikeProduct(int i) {
		List<String> inventoryNow = new ArrayList<String>();
		List<String> sizeNow = new ArrayList<String>();
		List<String> sizeList = new ArrayList<String>();
		sizeSelector = new Select(
				chromeDriver.findElement(By.cssSelector("#product-options > div:nth-child(2) > select")));
		sizeSelector.getOptions().forEach(item -> {
			sizeList.add(item.getText());
		});
		if (sizeList.get(0).contains("Select"))
			sizeList.remove(0);
		int sl = sizeList.size();

		for (int j = 0; j < sizeList.size(); j++) {
			try {
				if (!checkProductUrl(i))
					j--;
				else {
					checkCurrency("USD");
					String s = sizeList.get(j);
					// refresh page to get correct inventory without #zone-error
					if (!inventoryNow.isEmpty() && !inventoryNow.get(j - 1).equals("20+"))
						chromeDriver.navigate().refresh();

					wait.until(ExpectedConditions
							.presenceOfElementLocated(By.cssSelector("#product-options > div:nth-child(2) > select")));
					wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#skuQty1")));

					sizeSelector = new Select(
							chromeDriver.findElement(By.cssSelector("#product-options > div:nth-child(2) > select")));
					qtySelector = new Select(chromeDriver.findElement(By.cssSelector("#skuQty1")));

					sizeSelector.selectByVisibleText(s);
					qtySelector.selectByVisibleText("20");

					// get response
					String submitResult = submitBag(i);
					if (submitResult == "timeout") {
						logger.info("Timeout item " + i + " size(" + sizeList.get(j) + ")\n" + productLink.get(i)
								+ seperator);
						j--;
					} else {
						inventoryNow.add(submitResult);
						sizeNow.add(s);
					}
				}
			} catch (Exception e) {
				if (processException(e, "FetchClothesLikeProduct", i) != SKIP)
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
		for (int j = 0; j < sl - 1; j++) {
			styleid.add(styleid.get(styleid.size() - 1));
			name.add(name.get(name.size() - 1));
			rating.add(rating.get(rating.size() - 1));
			reviewCount.add(reviewCount.get(reviewCount.size() - 1));
			regularPrice.add(regularPrice.get(regularPrice.size() - 1));
			salesPrice.add(salesPrice.get(salesPrice.size() - 1));
		}
		sizeList.clear();
	}

	public static void fetchGadgetLikeProduct(int i) {
		// just select quantity, no size
		sizeSelector = null;
		qtySelector = new Select(chromeDriver.findElement(By.cssSelector("#skuQty1")));
		qtySelector.selectByVisibleText("20");

		for (int j = 0; j < 1; j++) {
			try {
				if (!checkProductUrl(i))
					j--;
				else {
					checkCurrency("USD");
					String submitResult = submitBag(i);
					if (submitResult == "timeout") {
						logger.info("Timeout item: id(" + productLink.get(i) + ") size(NA)" + seperator);
						j--;
					} else {
						inventory.add(submitResult);
						size.add("NA");
					}
				}
			} catch (Exception e) {
				if (processException(e, "FetchClothesLikeProduct", i) != SKIP)
					j--;
			}
		}
	}

	public static String submitBag(int index) {
		String left = "20+";
		try {
			WebElement submitBagBtn = chromeDriver.findElement(By.cssSelector("#add-to-bag"));
			wait.until(ExpectedConditions.elementToBeClickable(submitBagBtn));
			wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".jqmOverlay")));
			submitBagBtn.click();
			wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#pc-overflow")));
			Thread.sleep(1500);
			// check if exists inventory message
			Pattern selloutPtn = Pattern.compile("Only \\((?<leftAmt>\\d+)\\) left");
			List<WebElement> sellOutDivs = chromeDriver.findElements(By.cssSelector("#zone-error"));
			if (!sellOutDivs.isEmpty()) {
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
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".jqmOverlay")));
		Select optionSelector = new Select(
				chromeDriver.findElement(By.cssSelector("#context-selector-currency-select")));
		optionSelector.selectByValue(currency);
		WebElement submitBtn = chromeDriver.findElement(By.cssSelector(
				"#localizationPrefSave > div.context-selector-call-to-action > input[type='image']:nth-child(5)"));
		wait.until(ExpectedConditions.elementToBeClickable(submitBtn));
		submitBtn.click();
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".jqmOverlay")));
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
			try {
				WebElement countryBtn = chromeDriver.findElement(By.cssSelector("#ibf-countryName"));
				wait.until(ExpectedConditions.elementToBeClickable(countryBtn));
				countryBtn.click();
				wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#contextSelector")));
				Thread.sleep(5000);
				if (chromeDriver.findElement(By.cssSelector("#contextSelector")).isEnabled())
					changeCurrency(currency);
			} catch (Exception e) {
				processException(e, "checkCurrency", -1);
				checkCurrency(currency);
			}
		}
	}

	public static Boolean checkProductUrl(int i) {
		if (!chromeDriver.getCurrentUrl().trim().equals(productLink.get(i).trim())) {
			chromeDriver.get(productLink.get(i));
			logger.info("URL error!\nCurrent: " + chromeDriver.getCurrentUrl().trim() + "\nExpect : "
					+ productLink.get(i) + seperator);
			return false;
		}
		return true;
	}

	public static void clearPopup() {
		List<WebElement> closePopupBtn_shippment = chromeDriver.findElements(By.cssSelector("#closeButton"));
		if (!closePopupBtn_shippment.isEmpty()) {
			wait.until(ExpectedConditions.elementToBeClickable(closePopupBtn_shippment.get(0)));
			closePopupBtn_shippment.get(0).click();
		}

		List<WebElement> closePopupBtn_giftCard = chromeDriver
				.findElements(By.cssSelector("#jModal > table > tbody > tr.modalControls > td:nth-child(2) > a"));
		if (!closePopupBtn_giftCard.isEmpty()) {
			closePopupBtn_giftCard.get(0).click();
		}
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector("#tinymask")));
	}

	public static void clearAlert() {
		try {
			Alert alert = new WebDriverWait(chromeDriver, 1).until(ExpectedConditions.alertIsPresent());
			if (alert != null) {
				logger.info("Alert popout" + seperator);
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
		regularPrice.clear();
		salesPrice.clear();
		size.clear();
		inventory.clear();
	}

	public static void writeCSV(int s, int e) {
		boolean alreadyExists;
		CsvWriter csvOutput;

		int sInd = (s == -1 && e == -1) ? 0 : s;
		int eInd = (s == -1 && e == -1) ? styleid.size() : e;
		String wf = (s == -1 && e == -1) ? csvFile_backup : csvFile;

		try {
			alreadyExists = new File(wf).exists();
			csvOutput = new CsvWriter(new FileWriter(wf, true), ',');

			// write header
			if (!alreadyExists)
				csvOutput.writeRecord(new String[] { "timeStamp", "styleid", "name", "rating", "reviewCount",
						"priceNow", "priceWas", "size", "inventory" });

			// write records
			for (int i = sInd; i < eInd; i++) {
				csvOutput.write(timeNow);
				csvOutput.write(styleid.get(i));
				csvOutput.write(name.get(i));
				csvOutput.write(rating.get(i));
				csvOutput.write(reviewCount.get(i));
				csvOutput.write(regularPrice.get(i));
				csvOutput.write(salesPrice.get(i));
				csvOutput.write(size.get(i));
				csvOutput.write(inventory.get(i));
				csvOutput.endRecord();
			}

			csvOutput.close();

			if (s == -1 && e == -1)
				logger.info("Successfully write (from " + timeNow + ") " + productLink.size() + " items ("
						+ styleid.size() + " records) to " + csvFile + seperator + seperator + seperator + seperator
						+ seperator + seperator);

		} catch (IOException x) {
			// e.printStackTrace();
			String file = (s == -1 && e == -1) ? "whbm_backup" + timeNow.replaceAll("[ :-]", "_") + ".csv"
					: "whbm_" + timeNow.replaceAll("[ :-]", "_") + ".csv";
			alreadyExists = new File(file).exists();
			logger.info("Warning: csvFile " + csvFile + " in use. Save to " + file + seperator);
			try {
				csvOutput = new CsvWriter(new FileWriter(file, true), ',');
				if (!alreadyExists)
					csvOutput.writeRecord(new String[] { "timeStamp", "styleid", "name", "rating", "reviewCount",
							"priceNow", "priceWas", "size", "inventory" });
				// write records
				for (int i = 0; i < inventory.size(); i++) {
					csvOutput.write(timeNow);
					csvOutput.write(styleid.get(i));
					csvOutput.write(name.get(i));
					csvOutput.write(rating.get(i));
					csvOutput.write(reviewCount.get(i));
					csvOutput.write(regularPrice.get(i));
					csvOutput.write(salesPrice.get(i));
					csvOutput.write(size.get(i));
					csvOutput.write(inventory.get(i));
					csvOutput.endRecord();
				}
				if (s == -1 && e == -1)
					logger.info("Successfully write temp file (from " + timeNow + ") " + productLink.size() + " items ("
							+ styleid.size() + " records) to " + file + seperator);
				csvOutput.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

}
