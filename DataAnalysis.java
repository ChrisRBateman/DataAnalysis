
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * DataAnalysis parses a data file from a utility and 
 * calculates some simple summary statistics.
 */
public class DataAnalysis {
	
	public boolean debugFlag = false;
	
	// Data -------------------------------------------------------------------------------------------------
	
	@SuppressWarnings("unused")
	private class Data {
		int custID;
		int elecOrGas;
		boolean disconnectDoc;
		Date moveInDate;
		Date moveOutDate;
		int billYear;
		int billMonth;
		int spanDays;
		Date meterReadDate;
		String meterReadType;
		double consumption;
		String exceptionCode;
	}
	
	// Parser -----------------------------------------------------------------------------------------------
	
	private class Parser {
		
		private DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		private String filePath = "";
		private BufferedReader br = null;
		private String line = "";
		private int lineNum = 0;
		
		private Parser(String filePath) {
			this.filePath = filePath;
		}
		
		/**
		 * Creates a Data object from array of string values. If there are any problems 
		 * parsing the line then null is returned.
		 * 
		 * @param lineParts array of string values
		 * @param lineNum the line number
		 * @return a Data object or null
		 */
		private Data createDataObject(String[] lineParts, int lineNum) {
			String errorMsg = "";
			try {
				Data data = new Data();
				
				errorMsg = "int - [" + lineParts[0] + "] for [CustID]";
				data.custID = Integer.parseInt(lineParts[0]);
				
				errorMsg = "int - [" + lineParts[1] + "] for [ElecOrGas]";
				data.elecOrGas = Integer.parseInt(lineParts[1]);
				
				data.disconnectDoc = lineParts[2].equalsIgnoreCase("Y"); 
				
				errorMsg = "date - [" + lineParts[3] + "] for [Move In Date]";
				data.moveInDate = dateFormat.parse(lineParts[3]);
				
				errorMsg = "date - [" + lineParts[4] + "] for [Move Out Date]";
				data.moveOutDate = dateFormat.parse(lineParts[4]);
				
				errorMsg = "int - [" + lineParts[5] + "] for [Bill Year]";
				data.billYear = Integer.parseInt(lineParts[5]);
				
				errorMsg = "int - [" + lineParts[6] + "] for [Bill Month]";
				data.billMonth = Integer.parseInt(lineParts[6]);
				
				errorMsg = "int - [" + lineParts[7] + "] for [Span Days]";
				data.spanDays = Integer.parseInt(lineParts[7]);
				
				errorMsg = "date - [" + lineParts[8] + "] for [Meter Read Date]";
				data.meterReadDate = dateFormat.parse(lineParts[8]);
				
				data.meterReadType = lineParts[9];
			
				errorMsg = "double - [" + lineParts[10] + "] for [Consumption]";
				data.consumption = Double.parseDouble(lineParts[10]); 
				if (data.consumption > 200000.0) {
					errorMsg = "out of range - [" + lineParts[10] + "] for [Consumption]";
					if (debugFlag) {
						System.err.println("Error " + errorMsg + "[line:" + lineNum + "]");
					}
					return null;
				}
			
				return data;
			}
			catch (Exception e) {
				if (debugFlag) {
					System.err.println("Error parsing " + errorMsg + "[line:" + lineNum + "]");
				}
			}
			
			return null;
		}
		
		/**
		 * Parses a string of data values into a Data object. If there are any problems 
		 * parsing the line then null is returned.
		 * 
		 * @param line a string of data values
		 * @param lineNum the line number
		 * @return a Data object or null if an error occurs
		 */
		private Data parseLine(String line, int lineNum) {
			Data data = null;
			if (line != null) {
				String[] lineParts = line.split("\\|");
				if (lineParts.length == 11) {
					data = createDataObject(lineParts, lineNum);
				}
				else if (lineParts.length == 12) {
					data = createDataObject(lineParts, lineNum);
					if (data != null) {
						data.exceptionCode = lineParts[11];
					}
				}	
			}
			
			return data;
		}
		
		/**
		 * Returns true if there is another line available to parse.
		 * 
		 * @return true if there is another line available to parse; otherwise false
		 */
		public boolean next() {
			try {
				if (br == null) {
					GZIPInputStream gzip = new GZIPInputStream(new FileInputStream(filePath));
					br = new BufferedReader(new InputStreamReader(gzip));
					// We can ignore the first line
					br.readLine();
					lineNum = 2;
				}
				
				line = br.readLine();
				if (line == null) {
					br.close();
					br = null;
					return false;
				}
				else {
					return true;
				}
			}
			catch (Exception e) {
				System.err.println("Error accessing file - message : " + e.getMessage());
				return false;
			}
		}
		
		/**
		 * Returns the currently available Data object. May return null if a parsing error
		 * occurs.
		 * 
		 * @return a Data object or null
		 */
		public Data getData() {
			return parseLine(line, lineNum++);
		}
	}
	
	// Statistics ------------------------------------------------------------------------------------------- 
	
	private class Statistics {
		
		private class Consumption {
			int count = 0; 
			double total = 0.0;
		}
		
		private Set<Integer> uniqueCustomers = new HashSet<>();
		
		private Set<Integer> electricityOnlyCustomers = new HashSet<>(); 
		private Set<Integer> gasOnlyCustomers = new HashSet<>();
		private Set<Integer> electricityAndGasCustomers = null;
		
		private Map<Integer, Integer> electricityReadingsForCustomers = new TreeMap<>();
		private Map<Integer, Integer> gasReadingsForCustomers = new TreeMap<>();
		private Map<Integer, Consumption> electricityConsumptionPerMonth = new TreeMap<>();
		private Map<Integer, Consumption> gasConsumptionPerMonth = new TreeMap<>();
		
		/**
		 * Increments the reading count for a customer.
		 * 
		 * @param customerReadings the customer readings map
		 * @param data the Data object
		 */
		private void incCustomerReadings(Map<Integer, Integer> customerReadings, Data data) {
			int id = data.custID;
			Integer value = 1;
			if (customerReadings.containsKey(id)) {
				value = customerReadings.get(id) + 1;
			}
			customerReadings.put(id, value);
		}
		
		/**
		 * Increments the consumption count for a month.
		 * 
		 * @param cpm consumption per month map
		 * @param data the Data object
		 */
		private void incConsumptionPerMonth(Map<Integer, Consumption> cpm, Data data) {
			if (data.meterReadDate != null) {
				double consumption = data.consumption;
				Calendar cal = Calendar.getInstance();
				cal.setTime(data.meterReadDate);
				int month = cal.get(Calendar.MONTH);
				
				Consumption con;
				if (cpm.containsKey(month)) {
					con = cpm.get(month);
					
				}
				else {
					con = new Consumption(); 
				}
				con.count++;
				con.total += consumption;
				cpm.put(month, con);
			}
		}
		
		/**
		 * Initialize Statistics with data. The data is streamed from a Parser.
		 * 
		 * @param parser the Parser where data is streamed from
		 */
		public void init(Parser parser) {
			Map<Integer, Integer> electricityCustomerReadings = new HashMap<>();
			Map<Integer, Integer> gasCustomerReadings = new HashMap<>();
			
			while (parser.next()) {
				Data data = parser.getData();
				if (data != null) {
					uniqueCustomers.add(data.custID);
					
					switch (data.elecOrGas) {
						case 1: {
							electricityOnlyCustomers.add(data.custID);
							
							// Store the number of readings per electricity customer
							incCustomerReadings(electricityCustomerReadings, data);
							
							// Store the electricity consumption values per month
							incConsumptionPerMonth(electricityConsumptionPerMonth, data);
							
							break;
						}
						case 2: {
							gasOnlyCustomers.add(data.custID);
							
							// Store the number of readings per gas customer
							incCustomerReadings(gasCustomerReadings, data);
							
							// Store the gas consumption values per month
							incConsumptionPerMonth(gasConsumptionPerMonth, data);
							
							break;
						}
						default:
							break;
					}
				}
			}
			
			electricityAndGasCustomers = new HashSet<Integer>(electricityOnlyCustomers);
			electricityAndGasCustomers.retainAll(gasOnlyCustomers);
			
			electricityOnlyCustomers.removeAll(electricityAndGasCustomers);
			gasOnlyCustomers.removeAll(electricityAndGasCustomers);
			
			// Get the lists of reading counts for electricity and gas
			List<Integer> electricityReadings = new ArrayList<Integer>(electricityCustomerReadings.values());
			List<Integer> gasReadings = new ArrayList<Integer>(gasCustomerReadings.values());
			
			// Group readings/customers into maps
			for (Integer readings : electricityReadings) {
				Integer customers = 1;
				if (electricityReadingsForCustomers.containsKey(readings)) {
					customers = electricityReadingsForCustomers.get(readings) + 1;
				}
				electricityReadingsForCustomers.put(readings, customers);
			}
			for (Integer readings : gasReadings) {
				Integer customers = 1;
				if (gasReadingsForCustomers.containsKey(readings)) {
					customers = gasReadingsForCustomers.get(readings) + 1;
				}
				gasReadingsForCustomers.put(readings, customers);
			}
		}
		
		/**
		 * Returns the number of unique customers.
		 * 
		 * @return unique customer count 
		 */
		public int getUniqueCustomerCount() {
			return uniqueCustomers.size();
		}
		
		/**
		 * Returns the number of electricity only customers.
		 * 
		 * @return electricity only customer count 
		 */
		public int getElectricityOnlyCustomerCount() {
			return electricityOnlyCustomers.size();
		}
		
		/**
		 * Returns the number of gas only customers.
		 * 
		 * @return gas only customer count 
		 */
		public int getGasOnlyCustomerCount() {
			return gasOnlyCustomers.size();
		}
		
		/**
		 * Returns the number of electricity and gas customers.
		 * 
		 * @return electricity and gas customer count 
		 */
		public int getElectricityAndGasCustomerCount() {
			return electricityAndGasCustomers.size();
		}
		
		/**
		 * Returns a electricity readings for customers map.
		 * 
		 * @return electricity readings for customers map
		 */
		public Map<Integer, Integer> getElectricityReadingsForCustomers() {
			return electricityReadingsForCustomers; 
		}
		
		/**
		 * Returns a gas readings for customers map.
		 * 
		 * @return gas readings for customers map
		 */
		public Map<Integer, Integer> getGasReadingsForCustomers() {
			return gasReadingsForCustomers; 
		}
		
		/**
		 * Converts internal consumption per month to external map.
		 * 
		 * @param gpm the internal consumption per month
		 * @return external map
		 */
		private Map<Integer, Double> getConsumptionPerMonth(Map<Integer, Consumption> cpm) {
			Map<Integer, Double> map = new TreeMap<>();
			for (Map.Entry<Integer, Consumption> entry : cpm.entrySet()) {
				Consumption con = entry.getValue();
			    map.put(entry.getKey(), (con.count == 0 ? con.total : con.total / con.count));
			}
			return map; 
		}
		
		/**
		 * Returns a electricity consumption per month map.
		 * 
		 * @return electricity consumption per month map
		 */
		public Map<Integer, Double> getElectricityConsumptionPerMonth() {
			return getConsumptionPerMonth(electricityConsumptionPerMonth); 
		}
		
		/**
		 * Returns a gas consumption per month map.
		 * 
		 * @return gas consumption per month map
		 */
		public Map<Integer, Double> getGasConsumptionPerMonth() {
			return getConsumptionPerMonth(gasConsumptionPerMonth); 
		}
	}
	
	// main -------------------------------------------------------------------------------------------------

	/**
	 * Application entry point.
	 * 
	 * @param args array of command line parameters
	 */
	public static void main(String[] args) {
		DataAnalysis app = new DataAnalysis();		
		app.run(args);
	}
	
	/**
	 * Print usage information.
	 */
	public void usage() {
		System.out.println("java DataAnalysis [-p] file");
		System.out.println("    where");
		System.out.println("        -p   - print parser errors");
		System.out.println("        file - is a gzipped pipe delimited text file");
	}
	
	/**
	 * Returns month name based on index.
	 * 
	 * @param index the month index (0 - 11)
	 * @return month name or empty string if index out of range
	 */
	public String getMonthName(int index) {
		String months[] = {"January", "February", "March", "April",
                "May", "June", "July", "August", "September",
                "October", "November", "December"};
		return (index <= 11 && index >= 0) ? months[index] : "";
	}
	
	/**
	 * Run application.
	 * 
	 * @param args array of command line parameters
	 */
	public void run(String[] args) {
		String filePath = "";
		switch (args.length) {
			case 1:
				filePath = args[0];
				break;
				
			case 2:
				debugFlag = (args[0].equalsIgnoreCase("-p"));
				filePath = args[1];
				break;
				
			default:
				usage();
				return;
		}
		
		Parser parser = new Parser(filePath);
		Statistics stats = new Statistics();
		
		stats.init(parser);
			
		int uniqueCustomerCount = stats.getUniqueCustomerCount();
		int electricityOnlyCustomerCount = stats.getElectricityOnlyCustomerCount();
		int gasOnlyCustomerCount = stats.getGasOnlyCustomerCount();
		int electricityAndGasOnlyCustomerCount = stats.getElectricityAndGasCustomerCount();
			
		System.out.println();
		System.out.println("Unique Customers              : " + uniqueCustomerCount);
		System.out.println("Electricity Only Customers    : " + electricityOnlyCustomerCount);
		System.out.println("Gas Only Customers            : " + gasOnlyCustomerCount);
		System.out.println("Electricity And Gas Customers : " + electricityAndGasOnlyCustomerCount);
			
		Map<Integer, Integer> electricityHistogram = stats.getElectricityReadingsForCustomers();
		Map<Integer, Integer> gasHistogram = stats.getGasReadingsForCustomers();
			
		System.out.println();
		System.out.println("Electricity");
		System.out.println("Number of meter readings: Number of customers");
		for (Map.Entry<Integer, Integer> entry : electricityHistogram.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
			
		System.out.println();
		System.out.println("Gas");
		System.out.println("Number of meter readings: Number of customers");
		for (Map.Entry<Integer, Integer> entry : gasHistogram.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
			
		Map<Integer, Double> electricityConsumption = stats.getElectricityConsumptionPerMonth();
		Map<Integer, Double> gasConsumption = stats.getGasConsumptionPerMonth();
			
		DecimalFormat df = new DecimalFormat("###.#");
		System.out.println();
		System.out.println("Electricity");
		for (Map.Entry<Integer, Double> entry : electricityConsumption.entrySet()) {
			System.out.println(getMonthName(entry.getKey()) + ": " + df.format(entry.getValue()));
		}
			
		System.out.println();
		System.out.println("Gas");
		for (Map.Entry<Integer, Double> entry : gasConsumption.entrySet()) {
			System.out.println(getMonthName(entry.getKey()) + ": " + df.format(entry.getValue()));
		}
	}
}
