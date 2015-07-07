import java.util.ArrayList;
import java.util.Collections;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.List;

public class SubscriptionRecordQueue extends RecordQueue {
	
	List<Record> queue;
	ConcurrentHashMap<String, Integer> subscribers;
	String producerName;
	private static Object lock = new Object();
	private static Object valueAdded = new Object();
	long timeLastPrinted=0l, minTimeBetweenMessages=1000l;

	SubscriptionRecordQueue(){};
	
	SubscriptionRecordQueue(String id, int maxQueueLength) {
		this.subscribers = new ConcurrentHashMap<String, Integer>(10, 0.75f, 4);
		this.queue = Collections.synchronizedList(new ArrayList<Record>(maxQueueLength));
		this.maxQueueLength = maxQueueLength;
		this.id = id + ":SubRecQ";
		this.producerName="";
	}
	
	public void subscribe(String subscriber){
		synchronized(lock){
			if(!subscribers.containsKey(subscriber)){
				subscribers.put(subscriber, new Integer(0));
			}
		}
	}
	
	public boolean registerProducer(String producer){
		if(producer == "" && producerName != ""){
			return false;
		}
		producerName = producer;
		return true;
	}
	
	public boolean unregisterProducer(String producer){
		if(producerName != producer || producer == ""){
			return false;
		}
		producerName="";
		return true;
		
	}
	
	public void unsubscribe(String subscriber){
		synchronized(lock){
			subscribers.remove(subscriber);
		}
	}
	
	public int queueSize() {
		return queue.size();
	}
	
	public String printRecords() {
		String records = "";
		synchronized(lock){
			ListIterator<Record> i = queue.listIterator();
			while(i.hasNext()){
				records = i.next().toString() + "\n";
			}
		}
		return records;
	}
	
	public String printNewestRecords(int numRecords) {
		String records = "";
		synchronized(lock){
			if(numRecords > queue.size()) {
				numRecords = queue.size();
			}
			ListIterator<Record> i = queue.listIterator(queue.size() - numRecords);
			for (int x=0; x<numRecords; x++) {
				records = records + i.next().toString() + "\n";
			}
		}
		return records;
	}
	
	public Record[] dumpNewestRecords(int numRecords){
		synchronized(lock){
			if(queue.size() < numRecords) {
				numRecords = queue.size();
			}
			Record[] outputRecords = new Record[numRecords];
			ListIterator<Record> i = queue.listIterator(queue.size() - numRecords);
			int x=0;
			while(i.hasNext()) {
				outputRecords[x] = i.next();
				x++;
			}
			return outputRecords;
		}
	}
	
	public Record[] dumpOldestRecords(int numRecords){
		synchronized(lock){
			if(queue.size() < numRecords) {
				numRecords = queue.size();
			}
			Record[] outputRecords = new Record[numRecords];
			ListIterator<Record> i = queue.listIterator();
			for(int x=0; x<numRecords; x++){
				outputRecords[x] = i.next();
			}
			return outputRecords;
		}
	}
	
	public Record[] dumpAllRecords(){
		synchronized(lock){
			Record[] outputRecords = new Record[queue.size()];
			ListIterator<Record> i = queue.listIterator();
			for(int x=0; x<queue.size(); x++){
				outputRecords[x] = i.next();
			}
			return outputRecords;
		}
	}
	
	public Record[] dumpRecordsFromTimeRange(long startTime, long endTime){
		synchronized(lock){
			ListIterator<Record> i = queue.listIterator();
			ListIterator<Record> ri = queue.listIterator(queue.size());
			boolean foundStart=false, foundEnd = false;
			int startPos = 0, endPos = queue.size() - 1;
			Record firstRecord = null;
			while (i.hasNext()){
				firstRecord = i.next();
				if(firstRecord.timestamp > startTime && firstRecord.timestamp <= endTime ) {
					foundStart = true;
					break;
				}
				startPos++;
			}
			if(foundStart){
				while(ri.hasPrevious()){
					Record r = i.previous();
					if(r.timestamp > startTime && r.timestamp <= endTime) {
						foundEnd = true;
						break;
					}
					endPos--;
				}
			}
			if(foundEnd){
				Record[] outputRecords = new Record[endPos - startPos + 1];
				outputRecords[0] = firstRecord;
				for(int x=1; x<(endPos - startPos + 1); x++){
					outputRecords[x] = i.next();
				}
				return outputRecords;
			}
		}
		return null;
	}

	/**
	public void ra(int positionToRemove, Record record){
		queue.remove(positionToRemove);
		queue.add(record);
	}
	public void us(int positionToRemove, boolean printStatus){
		Iterator<Map.Entry<String, Integer>> i = subscribers.entrySet().iterator();
		while (i.hasNext()) {
			Map.Entry<String, Integer> pair = (Map.Entry<String, Integer>)i.next();
			if( ((Integer)pair.getValue()).intValue() > positionToRemove){
				Integer newPosition = new Integer( ((Integer)pair.getValue()).intValue()- 1);
				subscribers.put((String)pair.getKey(), newPosition );
			} else {
				if(printStatus)
					System.err.println(System.currentTimeMillis() + " DEBUG: " + id + " Subscriber " + (String)pair.getKey() + " missed a Record in this queue that was dropped when the queue became full and a subsequent put was performed");
			}
		}
	}
	
	public void il(Record record){
		if(queue.size() == maxQueueLength){
			boolean printStatus=false;
			if(System.currentTimeMillis() - timeLastPrinted > minTimeBetweenMessages){
				printStatus = true;
				timeLastPrinted = System.currentTimeMillis();
			}
			int positionToRemove = (maxQueueLength / 2);
			if(printStatus)
				System.err.println(System.currentTimeMillis() + " DEBUG: " + id + " Request received to add element to full queue, dropping record from the middle of the queue.");
			us(positionToRemove, printStatus);
			ra(positionToRemove, record);
		} else {
			queue.add(record);
		}
	}
	public void lk(Record record) {
		synchronized(lock){
			il(record);
		}
	}
	public void va(){
		synchronized(valueAdded){
			na();
		}
	}
	public void na(){
		valueAdded.notifyAll();
	}
	public boolean put(Record record) {
		lk(record);
		va();
		return true;
	}
	**/
	
	public boolean put(Record record) {
		synchronized(lock){
			if(queue.size() == maxQueueLength){
				//boolean printStatus=false;
				//if(System.currentTimeMillis() - timeLastPrinted > minTimeBetweenMessages){
				//	printStatus = true;
				//	timeLastPrinted = System.currentTimeMillis();
				//}
				int positionToRemove = (maxQueueLength / 2);
				//if(printStatus)
				//	System.err.println(System.currentTimeMillis() + " DEBUG: " + id + " Request received to add element to full queue, dropping record from the middle of the queue.");
				Iterator<Map.Entry<String, Integer>> i = subscribers.entrySet().iterator();
				while (i.hasNext()) {
					Map.Entry<String, Integer> pair = (Map.Entry<String, Integer>)i.next();
					if( ((Integer)pair.getValue()).intValue() > positionToRemove){
						Integer newPosition = new Integer( ((Integer)pair.getValue()).intValue()- 1);
						subscribers.put((String)pair.getKey(), newPosition );
					//} else {
					//	if(printStatus)
					//		System.err.println(System.currentTimeMillis() + " DEBUG: " + id + " Subscriber " + (String)pair.getKey() + " missed a Record in this queue that was dropped when the queue became full and a subsequent put was performed");
					}
				}
				queue.remove(positionToRemove);
				queue.add(record);			
			} else {
				queue.add(record);
			}
			
		}
		//synchronized(valueAdded){
		//	valueAdded.notifyAll();
		//}
		return true;
	}
	
	public Record get(){
		return null;
	}

	public Record get(String subscriberName){
		boolean getComplete=false;
		boolean needToWaitForRecord=false;
		int waitTime=10;
		Record record = null;
		while(!getComplete){
			//Synchronize on lock for reading/writing SubscriberQueue contents.
			synchronized(lock) {
				int positionToRead = subscribers.get(subscriberName).intValue();
				//Check if we can read a value based on queue size and subscriber position.
				if(positionToRead == maxQueueLength || positionToRead == queue.size()) {
					needToWaitForRecord=true;
				//If we have a value we can read, then read it and adjust the positions.
				} else {
					record = queue.get(positionToRead);
					positionToRead++;
					subscribers.put(subscriberName,  new Integer(positionToRead));
					//Check if we can delete the element at the front of the queue.
					if(positionToRead == 1){
						boolean canDrop = true;
						Iterator<Map.Entry<String, Integer>> i = subscribers.entrySet().iterator();
						while (i.hasNext()) {
							Map.Entry<String, Integer> pair = (Map.Entry<String, Integer>)i.next();
							if( ((Integer)pair.getValue()).intValue() == 0) {
								canDrop=false;
								break;
							}
						}
						if(canDrop) {
							queue.remove(0);
							i = subscribers.entrySet().iterator();
							while (i.hasNext()) {
								Map.Entry<String, Integer> pair = (Map.Entry<String, Integer>)i.next();
								int newPosition = ((Integer)pair.getValue()).intValue();
								newPosition--;
								subscribers.put((String)pair.getKey(), new Integer(newPosition));
							}
						}
					}
					getComplete = true;
				}
			}
			if(needToWaitForRecord){
				try{
					synchronized(valueAdded) {
						valueAdded.wait(waitTime);
						if(waitTime<1000){
							waitTime *= 10;
						}
					}
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
		}
		return record;
	}
}
