package biz.brighta.obdandroid.utils;

import java.util.logging.Logger;

public class HexData {

    private final static Logger log = Logger.getLogger(HexData.class.getSimpleName());

    private static final String HEXES = "0123456789ABCDEF";
    private static final String HEX_INDICATOR = "0x";
    private static final String SPACE = " ";

    private HexData(){

    }

    public static String hexToString(byte[] data) {
        if(data == null)
            return null;
        StringBuilder hex = new StringBuilder(data.length * 2);
        for(int i = 0; i < data.length; i++) {
            byte dataAtIndex = data[i];
            hex.append(HEX_INDICATOR);
            hex.append(HEXES.charAt((dataAtIndex & 0xF0) >> 4)).append(HEXES.charAt(dataAtIndex & 0x0F));
            hex.append(SPACE);
        }
        return hex.toString();
    }

    public static byte[] stringToBytes(String hexString) {
        String stringProcessed = hexString.trim().replaceAll(HEX_INDICATOR, "");
        stringProcessed = stringProcessed.replaceAll("\\s+", "");
        byte data[] = new byte[stringProcessed.length() / 2];
        int i = 0;
        int j = 0;
        while (i <= stringProcessed.length() - 1) {
            byte character = (byte)Integer.parseInt(stringProcessed.substring(i, i+2), 16);
            data[j] = character;
            j++;
            i += 2;
        }
        return data;
    }

    public static String hex4digits(String id) {
        if(id.length() == 1)
            return "000" + id;
        if(id.length() == 2)
            return "00" + id;
        if(id.length() == 3)
            return "0" + id;
        else return id;
    }


}
