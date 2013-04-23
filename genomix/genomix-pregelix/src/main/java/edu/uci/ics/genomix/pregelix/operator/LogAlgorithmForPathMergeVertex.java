package edu.uci.ics.genomix.pregelix.operator;

import java.util.Iterator;

import org.apache.hadoop.io.NullWritable;

import edu.uci.ics.pregelix.api.graph.Vertex;
import edu.uci.ics.pregelix.api.job.PregelixJob;
import edu.uci.ics.genomix.pregelix.client.Client;
import edu.uci.ics.genomix.pregelix.format.LogAlgorithmForPathMergeInputFormat;
import edu.uci.ics.genomix.pregelix.format.LogAlgorithmForPathMergeOutputFormat;
import edu.uci.ics.genomix.pregelix.io.LogAlgorithmMessageWritable;
import edu.uci.ics.genomix.pregelix.io.ValueStateWritable;
import edu.uci.ics.genomix.pregelix.type.Message;
import edu.uci.ics.genomix.pregelix.type.State;
import edu.uci.ics.genomix.pregelix.util.GraphVertexOperation;
import edu.uci.ics.genomix.type.GeneCode;
import edu.uci.ics.genomix.type.KmerBytesWritable;
import edu.uci.ics.genomix.type.VKmerBytesWritable;
import edu.uci.ics.genomix.type.VKmerBytesWritableFactory;

/*
 * vertexId: BytesWritable
 * vertexValue: ValueStateWritable
 * edgeValue: NullWritable
 * message: LogAlgorithmMessageWritable
 * 
 * DNA:
 * A: 00
 * C: 01
 * G: 10
 * T: 11
 * 
 * succeed node
 *  A 00000001 1
 *  G 00000010 2
 *  C 00000100 4
 *  T 00001000 8
 * precursor node
 *  A 00010000 16
 *  G 00100000 32
 *  C 01000000 64
 *  T 10000000 128
 *  
 * For example, ONE LINE in input file: 00,01,10	0001,0010,
 * That means that vertexId is ACG, its succeed node is A and its precursor node is C.
 * The succeed node and precursor node will be stored in vertexValue and we don't use edgeValue.
 * The details about message are in edu.uci.ics.pregelix.example.io.MessageWritable. 
 */
public class LogAlgorithmForPathMergeVertex extends Vertex<KmerBytesWritable, ValueStateWritable, NullWritable, LogAlgorithmMessageWritable>{	
	
	public static final String KMER_SIZE = "LogAlgorithmForPathMergeVertex.kmerSize";
	public static final String ITERATIONS = "NaiveAlgorithmForPathMergeVertex.iteration";
	public static int kmerSize = -1;
	private int maxIteration = -1;
	
	private ValueStateWritable vertexVal = new ValueStateWritable();
	
	private LogAlgorithmMessageWritable msg = new LogAlgorithmMessageWritable();
	
	private VKmerBytesWritableFactory kmerFactory = new VKmerBytesWritableFactory(1);
	private VKmerBytesWritable vertexId = new VKmerBytesWritable(1); 
	private VKmerBytesWritable destVertexId = new VKmerBytesWritable(1); 
	private VKmerBytesWritable chainVertexId = new VKmerBytesWritable(1);
	private VKmerBytesWritable lastKmer = new VKmerBytesWritable(1);
	/**
	 * initiate kmerSize, maxIteration
	 */
	public void initVertex(){
		if(kmerSize == -1)
			kmerSize = getContext().getConfiguration().getInt(KMER_SIZE, 5);
        if (maxIteration < 0) 
            maxIteration = getContext().getConfiguration().getInt(ITERATIONS, 100);
		vertexId.set(getVertexId());
		vertexVal = getVertexValue();
	}
	/**
	 * get destination vertex
	 */
	public VKmerBytesWritable getNextDestVertexId(VKmerBytesWritable vertexId, byte geneCode){
		return kmerFactory.shiftKmerWithNextCode(vertexId, geneCode);
	}
	
	public VKmerBytesWritable getPreDestVertexId(VKmerBytesWritable vertexId, byte geneCode){
		return kmerFactory.shiftKmerWithPreCode(vertexId, geneCode);
	}
	
	public VKmerBytesWritable getNextDestVertexIdFromBitmap(VKmerBytesWritable chainVertexId, byte adjMap){
		return getDestVertexIdFromChain(chainVertexId, adjMap);//GeneCode.getGeneCodeFromBitMap((byte)(adjMap & 0x0F)
	}
	
	public VKmerBytesWritable getDestVertexIdFromChain(VKmerBytesWritable chainVertexId, byte adjMap){
		lastKmer.set(kmerFactory.getLastKmerFromChain(kmerSize, chainVertexId));
		return getNextDestVertexId(lastKmer, GeneCode.getGeneCodeFromBitMap((byte)(adjMap & 0x0F)));
	}
	/**
	 * head send message to all next nodes
	 */
	public void sendMsgToAllNextNodes(VKmerBytesWritable vertexId, byte adjMap){
		for(byte x = GeneCode.A; x<= GeneCode.T ; x++){
			if((adjMap & (1 << x)) != 0){
				destVertexId.set(getNextDestVertexId(vertexId, x));
				sendMsg(destVertexId, msg);
			}
		}
	}
	/**
	 * head send message to all previous nodes
	 */
	public void sendMsgToAllPreviousNodes(VKmerBytesWritable vertexId, byte adjMap){
		for(byte x = GeneCode.A; x<= GeneCode.T ; x++){
			if(((adjMap >> 4) & (1 << x)) != 0){
				destVertexId.set(getPreDestVertexId(vertexId, x));
				sendMsg(destVertexId, msg);
			}
		}
	}

	/**
	 * set vertex state
	 */
	public void setState(){
		if(msg.getMessage() == Message.START && 
				(vertexVal.getState() == State.MID_VERTEX || vertexVal.getState() == State.END_VERTEX)){
			vertexVal.setState(State.START_VERTEX);
			setVertexValue(vertexVal);
		}
		else if(msg.getMessage() == Message.END && vertexVal.getState() == State.MID_VERTEX){
			vertexVal.setState(State.END_VERTEX);
			setVertexValue(vertexVal);
			voteToHalt();
		}
		else
			voteToHalt();
	}
	/**
	 * send start message to next node
	 */
	public void sendStartMsgToNextNode(){
		msg.setMessage(Message.START);
		msg.setSourceVertexId(vertexId);
		sendMsg(destVertexId, msg);
		voteToHalt();
	}
	/**
	 * send end message to next node
	 */
	public void sendEndMsgToNextNode(){
		msg.setMessage(Message.END);
		msg.setSourceVertexId(vertexId);
		sendMsg(destVertexId, msg);
		voteToHalt();
	}
	/**
	 * send non message to next node
	 */
	public void sendNonMsgToNextNode(){
		msg.setMessage(Message.NON);
		msg.setSourceVertexId(vertexId);
		sendMsg(destVertexId, msg);
	}
	/**
	 * head send message to path
	 */
	public void sendMsgToPathVertex(VKmerBytesWritable chainVertexId, byte adjMap){
		if(GeneCode.getGeneCodeFromBitMap((byte)(vertexVal.getAdjMap() & 0x0F)) == -1) //|| lastKmer == null
			voteToHalt();
		else{
			destVertexId.set(getNextDestVertexIdFromBitmap(chainVertexId, adjMap));
			if(vertexVal.getState() == State.START_VERTEX){
				sendStartMsgToNextNode();
			}
			else if(vertexVal.getState() != State.END_VERTEX && vertexVal.getState() != State.FINAL_DELETE){
				sendEndMsgToNextNode();
			}
		}
	}
	/**
	 * path send message to head 
	 */
	public void responseMsgToHeadVertex(){
		if(vertexVal.getLengthOfMergeChain() == -1){
			vertexVal.setMergeChain(vertexId);
			setVertexValue(vertexVal);
		}
		msg.set(msg.getSourceVertexId(), vertexVal.getMergeChain(), vertexVal.getAdjMap(), msg.getMessage(), vertexVal.getState());
		setMessageType(msg.getMessage());
		destVertexId.set(msg.getSourceVertexId());
		sendMsg(destVertexId,msg);
	}
	/**
	 * set message type
	 */
	public void setMessageType(int message){
		//kill Message because it has been merged by the head
		if(vertexVal.getState() == State.END_VERTEX || vertexVal.getState() == State.FINAL_DELETE){
			msg.setMessage(Message.END);
			vertexVal.setState(State.FINAL_DELETE);
			setVertexValue(vertexVal);
			//deleteVertex(getVertexId());
		}
		else
			msg.setMessage(Message.NON);
		
		if(message == Message.START){
			vertexVal.setState(State.TODELETE);
			setVertexValue(vertexVal);
		}
	}
	/**
	 *  set vertexValue's state chainVertexId, value
	 */
	public void setVertexValueAttributes(){
		if(msg.getMessage() == Message.END){
			if(vertexVal.getState() != State.START_VERTEX)
				vertexVal.setState(State.END_VERTEX);
			else
				vertexVal.setState(State.FINAL_VERTEX);
		}
			
		if(getSuperstep() == 5)
			chainVertexId.set(vertexId);
		else
			chainVertexId.set(vertexVal.getMergeChain());
		lastKmer.set(kmerFactory.getLastKmerFromChain(msg.getLengthOfChain() - kmerSize + 1, msg.getChainVertexId()));
		chainVertexId.set(kmerFactory.mergeTwoKmer(chainVertexId, lastKmer));
		vertexVal.setMergeChain(chainVertexId);
		
		byte tmpVertexValue = GraphVertexOperation.updateRightNeighber(getVertexValue().getAdjMap(), msg.getAdjMap());
		vertexVal.setAdjMap(tmpVertexValue);
	}
	/**
	 *  send message to self
	 */
	public void sendMsgToSelf(){
		if(msg.getMessage() != Message.END){
			setVertexValue(vertexVal);
			msg.reset(); //reset
			msg.setAdjMap(vertexVal.getAdjMap());
			sendMsg(vertexId,msg);
		}
	}
	/**
	 * start sending message
	 */
	public void startSendMsg(){
		if(GraphVertexOperation.isHeadVertex(vertexVal.getAdjMap())){
			msg.set(vertexId, chainVertexId, (byte)0, Message.START, State.NON_VERTEX); //msg.set(null, (byte)0, chainVertexId, Message.START, State.NON_VERTEX);
			sendMsgToAllNextNodes(vertexId, vertexVal.getAdjMap());
			voteToHalt();
		}
		if(GraphVertexOperation.isRearVertex(vertexVal.getAdjMap())){
			msg.set(vertexId, chainVertexId, (byte)0, Message.END, State.NON_VERTEX);
			sendMsgToAllPreviousNodes(vertexId, vertexVal.getAdjMap());
			voteToHalt();
		}
		if(GraphVertexOperation.isPathVertex(vertexVal.getAdjMap())){
			vertexVal.setState(State.MID_VERTEX);
			setVertexValue(vertexVal);
		}
	}
	/**
	 *  initiate head, rear and path node
	 */
	public void initState(Iterator<LogAlgorithmMessageWritable> msgIterator){
		while(msgIterator.hasNext()){
			if(!GraphVertexOperation.isPathVertex(vertexVal.getAdjMap())){
				msgIterator.next();
				voteToHalt();
			}
			else{
				msg = msgIterator.next();
				setState();
			}
		}
	}
	/**
	 * head send message to path
	 */
	public void sendMsgToPathVertex(Iterator<LogAlgorithmMessageWritable> msgIterator){
		if(getSuperstep() == 3){
			msg.reset();
			sendMsgToPathVertex(vertexId, vertexVal.getAdjMap());
		}
		else{
			if(msgIterator.hasNext()){
				msg = msgIterator.next();
				sendMsgToPathVertex(vertexVal.getMergeChain(), msg.getAdjMap());
			}
		}
	}
	/**
	 * path response message to head
	 */
	public void responseMsgToHeadVertex(Iterator<LogAlgorithmMessageWritable> msgIterator){
		if(msgIterator.hasNext()){
			msg = msgIterator.next();
			responseMsgToHeadVertex();
			//voteToHalt();
		}
		else{
			if(getVertexValue().getState() != State.START_VERTEX 
					&& getVertexValue().getState() != State.END_VERTEX && getVertexValue().getState() != State.FINAL_DELETE){
				//vertexVal.setState(State.KILL_SELF);
				//setVertexValue(vertexVal);
				//voteToHalt();
				deleteVertex(getVertexId());//killSelf because it doesn't receive any message
			}
		}
	}
	/**
	 * merge chainVertex and store in vertexVal.chainVertexId
	 */
	public void mergeChainVertex(Iterator<LogAlgorithmMessageWritable> msgIterator){
		if(msgIterator.hasNext()){
			msg = msgIterator.next();
			setVertexValueAttributes();
			sendMsgToSelf();
		}
		if(vertexVal.getState() == State.END_VERTEX || vertexVal.getState() == State.FINAL_DELETE){
			voteToHalt();
		}
		if(vertexVal.getState() == State.FINAL_VERTEX){
			//String source = vertexVal.getMergeChain().toString();
			voteToHalt();
		}
	}
	@Override
	public void compute(Iterator<LogAlgorithmMessageWritable> msgIterator) {
		initVertex();
		if(vertexVal.getState() != State.NON_EXIST && vertexVal.getState() != State.KILL_SELF){
			if (getSuperstep() == 1) 
				startSendMsg();
			else if(getSuperstep() == 2)
				initState(msgIterator);
			else if(getSuperstep()%3 == 0 && getSuperstep() <= maxIteration){
				sendMsgToPathVertex(msgIterator);
			}
			else if(getSuperstep()%3 == 1 && getSuperstep() <= maxIteration){
				responseMsgToHeadVertex(msgIterator);
			}
			else if(getSuperstep()%3 == 2 && getSuperstep() <= maxIteration){
				if(vertexVal.getState() == State.TODELETE){ //|| vertexVal.getState() == State.KILL_SELF)
					//vertexVal.setState(State.NON_EXIST);
					//setVertexValue(vertexVal);
					//voteToHalt();
					deleteVertex(getVertexId()); //killSelf  
				}
				else{
					mergeChainVertex(msgIterator);
				}
			}
		}
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
        PregelixJob job = new PregelixJob(LogAlgorithmForPathMergeVertex.class.getSimpleName());
        job.setVertexClass(LogAlgorithmForPathMergeVertex.class);
        /**
         * BinaryInput and BinaryOutput~/
         */
        job.setVertexInputFormatClass(LogAlgorithmForPathMergeInputFormat.class); 
        job.setVertexOutputFormatClass(LogAlgorithmForPathMergeOutputFormat.class); 
        job.setOutputKeyClass(KmerBytesWritable.class);
        job.setOutputValueClass(ValueStateWritable.class);
        job.setDynamicVertexValueSize(true);
        Client.run(args, job);
	}
}