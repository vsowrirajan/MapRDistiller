import java.io.RandomAccessFile;
import java.math.BigInteger;

public class NetworkInterfaceRecord extends Record {
	String name, duplex;
	boolean fullDuplex;
	int carrier, speed, tx_queue_len;
	BigInteger collisions, rx_bytes, rx_dropped, rx_errors, rx_packets, tx_bytes, tx_dropped, tx_errors, tx_packets;
	
	public static NetworkInterfaceRecord produceRecord(String ifName){
		NetworkInterfaceRecord newrecord = new NetworkInterfaceRecord();
		
		newrecord.timestamp = System.currentTimeMillis();
		newrecord.name = ifName;
		
		newrecord.duplex = readStringFromFile("/sys/class/net/" + newrecord.name + "/duplex");
		if(newrecord.duplex.equals("full")){
			newrecord.fullDuplex = true;
		} else {
			newrecord.fullDuplex = false;
		}
		newrecord.carrier = readIntFromFile("/sys/class/net/" + newrecord.name + "/carrier");
		newrecord.speed = readIntFromFile("/sys/class/net/" + newrecord.name + "/speed");
		newrecord.tx_queue_len = readIntFromFile("/sys/class/net/" + newrecord.name + "/tx_queue_len");
		newrecord.collisions = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/collisions");
		newrecord.rx_bytes = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/rx_bytes");
		newrecord.rx_dropped = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/rx_dropped");
		newrecord.rx_errors = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/rx_errors");
		newrecord.rx_packets = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/rx_packets");
		newrecord.tx_bytes = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/tx_bytes");
		newrecord.tx_dropped = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/tx_dropped");
		newrecord.tx_errors = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/tx_errors");
		newrecord.tx_packets = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/tx_packets");
	
		return newrecord;
	}

	public static String readStringFromFile(String path) {
		RandomAccessFile file;
		try {
			file = new RandomAccessFile(path, "r");
			String line = file.readLine();
			file.close();
			if(line != null){
				return line.trim().split("\\s+")[0];
			}
		} catch (Exception e) {
			System.err.println("Caught an exception while reading String from file " + path);
			e.printStackTrace();
		}
		return null;
	}
	public static BigInteger readBigIntegerFromFile(String path) {
		RandomAccessFile file;
		try {
			file = new RandomAccessFile(path, "r");
			String line = file.readLine();
			file.close();
			if(line != null){
				return new BigInteger(line.trim().split("\\s+")[0]);
			}
		} catch (Exception e) {
			System.err.println("Caught an exception while reading BigInteger file " + path);
			e.printStackTrace();
		}
		return null;
	}
	public static int readIntFromFile(String path) {
		RandomAccessFile file;
		try {
			file = new RandomAccessFile(path, "r");
			String line = file.readLine();
			file.close();
			if(line != null){
				String part = line.trim().split("\\s+")[0];
				int ret = Integer.parseInt(part);
				return ret;
			}
		} catch (Exception e) {
			System.err.println("Caught an exception while reading BigInteger file " + path);
			e.printStackTrace();
		}
		return -1;
	}
	
	public String toString() {
		return super.toString() + " network.interface name:" + name + " dup:" + fullDuplex + 
				" car:" + carrier + " sp:" + speed + " ql:" + tx_queue_len + " co:" + collisions + 
				" rx b:" + rx_bytes + " d:" + rx_dropped + " e:" + rx_errors + " p:" + rx_packets + 
				" tx b:" + tx_bytes + " d:" + tx_dropped + " e:" + tx_errors + " p:" + tx_packets;
	}
}
