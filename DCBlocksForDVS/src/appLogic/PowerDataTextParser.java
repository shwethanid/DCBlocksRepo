package appLogic;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import appLogicMessages.BusDataMsg;
import appLogicMessages.LineDataMsg;
import appLogicMessages.PowerMeasurementMsg;

public class PowerDataTextParser {
	private String busFilename = "";
	private String lineFilename = "";

	public PowerDataTextParser(String busFilename, String lineFilename) {
		this.busFilename = busFilename;
		this.lineFilename = lineFilename;
	}

	public final Boolean getPowerMeasurement(int busNum, int groupLimit,
			PowerMeasurementMsg powMsg) {
		Boolean isBusSuccess = true;
		Boolean isLineSuccess = true;

		isBusSuccess = readBusData(busNum, powMsg.getBusData());
		isLineSuccess = readLineDataBasedOnFromBus(busNum, powMsg);

		return isBusSuccess & isLineSuccess;
	}

	public Boolean readBusData(int busNum, BusDataMsg busData) {

		try {
			FileReader fileReader = new FileReader(this.busFilename);
			BufferedReader textReader = new BufferedReader(fileReader);

			String readLine;
			textReader.readLine();
			int busType = 0;
			int busNo = 0;
			Boolean isFnd = false;
			int i = 0;
			while (((readLine = textReader.readLine()) != null) && !isFnd) {
				
				if (i <= 1) {
					// Discard line
					//System.out.println("hhh");
				} else {

					String[] split = readLine.split("\\s+");
					busNo = Integer.parseInt(split[0].trim());
					//System.out.println("busNo " + busNo);
					if (busNo == busNum) {
						busData.setBusNum(busNo);
						

						busType = Integer.parseInt(split[1].trim());
						busData.setBusType(busType);
						busData
								.getVolt()
								.setVa(new appLogicData.Complex(Double
										.parseDouble(split[2].trim()), Double
										.parseDouble(split[3].trim())));
						busData
								.getVolt()
								.setVb(new appLogicData.Complex(Double
										.parseDouble(split[4].trim()), Double
										.parseDouble(split[5].trim())));
						busData
								.getVolt()
								.setVc(new appLogicData.Complex(Double
										.parseDouble(split[6].trim()), Double
										.parseDouble(split[7].trim())));

						busData
								.getCurr()
								.setIa(new appLogicData.Complex(Double
										.parseDouble(split[8].trim()), Double
										.parseDouble(split[9].trim())));
						busData
								.getCurr()
								.setIb(new appLogicData.Complex(Double
										.parseDouble(split[10].trim()), Double
										.parseDouble(split[11].trim())));
						busData
								.getCurr()
								.setIc(new appLogicData.Complex(Double
										.parseDouble(split[12].trim()), Double
										.parseDouble(split[13].trim())));

						busData.setShuntCap(
								Double.parseDouble(split[14].trim()));
						busData.setShuntReserv(
								Double.parseDouble(split[15].trim()));
						isFnd = true;
					}
				}
				i++;
			}

			textReader.close();

			return isFnd;
		} catch (IOException ex) {
			System.out.println("Error in readling bus data" + ex.getMessage());
			return false;
		}

	}

	Boolean readLineDataBasedOnFromBus(int busNum, PowerMeasurementMsg powMsg) {
		Boolean isFnd = true;

		try {

			FileReader fileReader = new FileReader(this.lineFilename);
			BufferedReader textReader = new BufferedReader(fileReader);

			String readLine;
			textReader.readLine();
			int busNo = 0;
			int i=0;
			
			while ((readLine = textReader.readLine()) != null) {
					String[] split = readLine.split("\\s+");

					//System.out.println(split[0]);
					busNo = Integer.parseInt(split[0].trim());
					if (busNo == busNum) {
						powMsg.getLineData().setFromBus(busNo);
						powMsg.getLineData().setToBus(Integer.parseInt(split[1].trim()));
						powMsg.getLineData().setR(Double.parseDouble(split[2].trim()));
						powMsg.getLineData().setX(Double.parseDouble(split[3].trim()));
						powMsg.getLineData().setC(Double.parseDouble(split[4].trim()));
						powMsg.getLineData().setTap(Double.parseDouble(split[5].trim()));
						//isFnd = true;
					}
			}

			textReader.close();
			return true;
		} catch (IOException ex) {
			System.out
					.println("Error in readling line data " + ex.getMessage());
			return false;
		}
	}
	
	Boolean readLineDataBasedOnToBus(int busNum, ArrayList<Integer> fromBusList, LineDataMsg lineData) {
		Boolean isFnd = true;

		try {

			FileReader fileReader = new FileReader(this.lineFilename);
			BufferedReader textReader = new BufferedReader(fileReader);

			String readLine;
			textReader.readLine();
			int fromBus = 0;
			int toBus = 0;
			
			while ((readLine = textReader.readLine()) != null) {
				String[] split = readLine.split("\\s+");

				// System.out.println(split[0]);
				fromBus = Integer.parseInt(split[0].trim());
				toBus = Integer.parseInt(split[1].trim());
				if ((toBus == busNum) && (!fromBusList.contains(fromBus))) {
					lineData.setFromBus(fromBus);
					lineData.setToBus(
							Integer.parseInt(split[1].trim()));
					lineData.setR(
							Double.parseDouble(split[2].trim()));
					lineData.setX(
							Double.parseDouble(split[3].trim()));
					lineData.setC(
							Double.parseDouble(split[4].trim()));
					lineData.setTap(
							Double.parseDouble(split[5].trim()));
					// isFnd = true;
				}
			}

			textReader.close();
			return true;
		} catch (IOException ex) {
			System.out
					.println("Error in readling line data " + ex.getMessage());
			return false;
		}

	}
}
