package com.flat20.fingerplay.socket;

import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import com.flat20.fingerplay.FingerPlayServer;
import com.flat20.fingerplay.Midi;
import com.flat20.fingerplay.MidiReceiver.IMidiListener;
import com.flat20.fingerplay.socket.commands.FingerReader;
import com.flat20.fingerplay.socket.commands.FingerReader.ISocketReceiver;
import com.flat20.fingerplay.socket.commands.FingerWriter;
import com.flat20.fingerplay.socket.commands.midi.MidiControlChange;
import com.flat20.fingerplay.socket.commands.midi.MidiNoteOff;
import com.flat20.fingerplay.socket.commands.midi.MidiPgmChange;
import com.flat20.fingerplay.socket.commands.midi.MidiSocketCommand;
import com.flat20.fingerplay.socket.commands.misc.DeviceList;
import com.flat20.fingerplay.socket.commands.misc.RequestMidiDeviceList;
import com.flat20.fingerplay.socket.commands.misc.SetMidiDevice;
import com.flat20.fingerplay.socket.commands.misc.Version;

public class ServerSocketThread  extends Thread implements ISocketReceiver, IMidiListener {

	final private Socket clientConn;
	
	final private DataInputStream inFromCLi;
	final private DataOutputStream outToCli;
	final private FingerWriter mWriter;

//	final private MidiReceiver mMidiReceiver;
	
	private boolean stopMe = false;
	private TrayIcon trayIco = null;
	private Image image =null;
	
	public ServerSocketThread(Socket clientConn, TrayIcon trayIco,Image image) throws IOException {
		this.clientConn = clientConn;
		
		this.trayIco = trayIco;
		this.image = image;
		inFromCLi = new DataInputStream(clientConn.getInputStream());
		outToCli = new DataOutputStream(new BufferedOutputStream(clientConn.getOutputStream()));
		mWriter = new FingerWriter(clientConn,outToCli);
	
		
	}

	public  Socket getClientConn(){
		return clientConn;
	}
	public void run() {
		try {

			// to read commands from input stream, receiver called on command is this
			final FingerReader reader = new FingerReader(inFromCLi, this);
			//System.out.println("run thread");
			while (!stopMe) {
					try {		
						//block till Reads one command
						reader.readCommand();
					} catch (SocketTimeoutException e) {
						
						closeAll();	
						
					}
			}
		
			closeAll();

		} catch (SocketException e) {
			//System.out.println(e);
		} catch (EOFException e) {
			//System.out.println(e);
		} catch (ArrayIndexOutOfBoundsException e) {
			System.out.println("Client message too long for buffer.");
			System.out.println(e);
		} catch(Exception e) {
			System.out.println("S: Error");
			e.printStackTrace();
		} finally {
			closeAll();
		}
		

		

		return;
	}

	
	private void closeAll(){
		
		try {
			clientConn.close();
			
			
				FingerPlayServer.getSocksClients().remove(this);
				int rest = FingerPlayServer.getSocksClients().size();
				Midi.number_of_connections = rest;
				
			if(SystemTray.isSupported()){
				if (rest==0){
					 trayIco.setImage(image);
				}
				 trayIco.displayMessage("Deconnexion", "Client disconnected.", TrayIcon.MessageType.INFO);
			}else {
				System.out.println("Client deconnexion : Client connexion was closed.");
			}
		} catch (IOException io) {
			System.out.println("Error closing connexion" + io.getMessage());
		}
		stopMe = true;
		
	}
	public void onVersion(Version clientVersion) throws Exception {
		System.out.println("Client version: " + clientVersion.message);

		Version version = new Version(FingerPlayServer.VERSION);

		mWriter.write(version);
	}

	public void onMidiSocketCommand(MidiSocketCommand socketCommand) throws Exception {
	//	System.out.println("onMidiSocketCommand = " + socketCommand.midiCommand + " channel = " + socketCommand.channel + ", data1 = " + socketCommand.data1 + " data2 = " + socketCommand.data2);
		synchronized (Midi.getInstance()) {
			Midi.getInstance().sendShortMessage(socketCommand.midiCommand, socketCommand.channel, socketCommand.data1, socketCommand.data2);						
		}
	}

	public void onRequestMidiDeviceList(RequestMidiDeviceList request) throws Exception {
		String[] deviceNames = Midi.getDeviceNames(false, true);

		String allDevices = "";
		for (int i=0; i<deviceNames.length; i++) {
			allDevices += deviceNames[i] + "%";
		}
		if (deviceNames.length > 0)
			allDevices = allDevices.substring(0, allDevices.length()-1);

		DeviceList deviceList = new DeviceList( allDevices );

		mWriter.write(deviceList);
	}
	
	public void stopMe(){
		stopMe=true;
	}

	public void onSetMidiDevice(SetMidiDevice ssm) throws Exception {
		if (!stopMe){
			
			String device = ssm.message;

			//System.out.println("Setting MIDI Device: " + device);
			synchronized (Midi.getInstance()) {
			
				Midi.getInstance().open(device, true); // true = bForOutput
				UdpOutput.getInstance().assignMidiUDPReceiver(device,clientConn.getInetAddress());
			}
		}
		
	}

	public void onDeviceList(DeviceList deviceList) throws Exception {
		
	}

	public void onControlChange(int channel, int control2, int value) {
		MidiControlChange mcc = new MidiControlChange(0xB0, channel, control2, value);
		try {
			mWriter.write( mcc );
		} catch (Exception e) {
			
		}
	}
	
	
	public void onProgramChange(int channel,  int value) {
		MidiPgmChange mpc = new MidiPgmChange(channel, value);
		try {
			mWriter.write( mpc );
		} catch (Exception e) {
			
		}
	}
	
	

	public void onNoteOff(int channel, int key, int velocity) {
		MidiNoteOff mno = new MidiNoteOff(channel, key, velocity);
		try {
			mWriter.write( mno );
		} catch (Exception e) {
			
		}
	}

	public void onNoteOn(int channel, int key, int velocity) {
		// System.out.println("note on : ");	
		MidiNoteOff mno = new MidiNoteOff(channel, key, velocity);
		try {
			mWriter.write( mno );
		} catch (Exception e) {
			
		}
	}

	public void onMidiSyncReceived(byte[] message) {
	       
	}

}
