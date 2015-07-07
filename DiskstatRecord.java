import java.math.BigInteger;

public class DiskstatRecord extends Record {
	int major_number;
	int minor_mumber;
	String device_name;
	BigInteger reads_completed_successfully;
	BigInteger reads_merged;
	BigInteger sectors_read;
	BigInteger time_spent_reading;
	BigInteger writes_completed;
	BigInteger writes_merged;
	BigInteger sectors_written;
	BigInteger time_spent_writing;
	BigInteger IOs_currently_in_progress;
	BigInteger time_spent_doing_IOs;
	BigInteger weighted_time_spent_doing_IOs;
	
	public String toString(){
		return super.toString() + " Diskstat dev:" + device_name + 
				" rcs " + reads_completed_successfully + 
				" rm " + reads_merged + 
				" sr " + sectors_read + 
				" tsr " + time_spent_reading + 
				" wc " + writes_completed + 
				" wm " + writes_merged + 
				" sw " + sectors_written +
				" tsw " + time_spent_writing + 
				" cip " + IOs_currently_in_progress + 
				" ts " + time_spent_doing_IOs + 
				" wts " + weighted_time_spent_doing_IOs;
	}

}
