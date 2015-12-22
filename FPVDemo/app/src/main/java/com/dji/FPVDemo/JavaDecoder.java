package com.dji.FPVDemo;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

public class JavaDecoder {
    private int packetLength = 0;
    private ByteBuffer accessUnitBuffer = ByteBuffer.allocate( 50000 );
    ArrayList<byte []> NAL_units = new ArrayList<byte []>();

    protected void splitNALunits(byte[] vBuffer){
        byte[] temp;
        int start=0;
        int end=0;
        for (int i=0;i<vBuffer.length;i++){
            if (vBuffer[i] == 0x00 && vBuffer[i+1] == 0x00 && vBuffer[i+2] == 0x01 && vBuffer[i+3] == 0x09){
                if (start != 0)
                    NAL_units.add(Arrays.copyOfRange(vBuffer,start,i-1));
                start=i+4;
            }

        }
    }

    protected void setDataToDecoder(byte[] videoBuffer, int size){
        splitNALunits(videoBuffer);
    }

}
