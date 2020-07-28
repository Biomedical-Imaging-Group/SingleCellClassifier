package ch.epfl.single_cell_classifier.utils;

import java.awt.Color;

import org.apache.commons.lang3.ArrayUtils;

import ij.process.LUT;
import inra.ijpb.util.ColorMaps;
import net.imglib2.display.ColorTable;
import net.imglib2.display.ColorTable8;

public class LUTHelper {
	private static byte[][] getGlasbeyBytes(){
		byte[][] glasbey = ColorMaps.createGlasbeyLut();

		byte[][] glasbeyGoodOrientation = new byte[3][glasbey.length];
		for(int i = 0; i < glasbey.length; ++i) {
			for(int j = 0; j < glasbey[i].length; ++j) {
				glasbeyGoodOrientation[j][i] = glasbey[i][j];
			}
		}

		return glasbeyGoodOrientation;
	}

	public static LUT getGlasbeyLUT() {
		byte[][] b = getGlasbeyBytes();
		LUT lut = new LUT(b[0], b[1], b[2]);

		return lut;
	}

	public static ColorTable getGlasbeyColorTable() {
		byte[][] b = getGlasbeyBytes();
		ColorTable colorTable = new ColorTable8(b);

		return colorTable;
	}

	private static byte[][] getIceBytes(int backgroundPos, boolean isBackgroundBlack){
		byte[][] ice;
		if(backgroundPos >= 0 && backgroundPos < 256) {
			ice = ColorMaps.createIceLut(255);
			// Set background to black
			if(isBackgroundBlack)
				ice = ArrayUtils.insert(backgroundPos, ice, new byte[] {(byte) 0,(byte) 0,(byte) 0});
			// Set background to white
			else
				ice = ArrayUtils.insert(backgroundPos, ice, new byte[] {(byte) 255,(byte) 255,(byte) 255});

		}else {
			ice = ColorMaps.createIceLut(256);
		}

		byte[][] iceGoodOrientation = new byte[3][ice.length];
		for(int i = 0; i < ice.length; ++i) {
			for(int j = 0; j < ice[i].length; ++j) {
				iceGoodOrientation[j][i] = ice[i][j];
			}
		}

		return iceGoodOrientation;
	}

	public static LUT getIceLUT(int backgroundPos, boolean isBackgroundBlack) {
		byte[][] b = getIceBytes(backgroundPos, isBackgroundBlack);
		LUT lut = new LUT(b[0], b[1], b[2]);

		return lut;
	}

	public static ColorTable getIceColorTable(int backgroundPos, boolean isBackgroundBlack) {
		byte[][] b = getIceBytes(backgroundPos, isBackgroundBlack);
		ColorTable colorTable = new ColorTable8(b);

		return colorTable;
	}

	private static byte[][] getColorBytes(Color color, int backgroundPos, boolean isBackgroundBlack) {
		byte[][] initColor = new byte[2][3];
		initColor[0] = new byte[] {0, 0, 0};
		initColor[1] = new byte[] {(byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue()};
		
		byte[][] colorBytes;
		if(backgroundPos >= 0 && backgroundPos < 256) {
			colorBytes = ColorMaps.interpolateLut(initColor, 255);
			if(isBackgroundBlack)
				colorBytes = ArrayUtils.insert(backgroundPos, colorBytes, new byte[] {0, 0, 0});
			// Set background to white
			else
				colorBytes = ArrayUtils.insert(backgroundPos, colorBytes, new byte[] {(byte) 255, (byte) 255, (byte) 255});
		}else {
			colorBytes = ColorMaps.interpolateLut(initColor, 256);
		}
		
		byte[][] colorGoodOrientation = new byte[3][colorBytes.length];
		for(int i = 0; i < colorBytes.length; ++i) {
			for(int j = 0; j < colorBytes[i].length; ++j) {
				colorGoodOrientation[j][i] = colorBytes[i][j];
			}
		}
		
		return colorGoodOrientation;
	}
	
	public static LUT getColorLUT(Color color, int backgroundPos, boolean isBackgroundBlack) {
		byte[][] b = getColorBytes(color, backgroundPos, isBackgroundBlack);
		LUT lut = new LUT(b[0], b[1], b[2]);

		return lut;
	}

	public static ColorTable getColorColorTable(Color color, int backgroundPos, boolean isBackgroundBlack) {
		byte[][] b = getColorBytes(color, backgroundPos, isBackgroundBlack);
		ColorTable colorTable = new ColorTable8(b);

		return colorTable;
	}
	
	private static byte[][] getColorsBytes(Color[] colors){
		byte[][] colorsByte = new byte[colors.length + 1][3];
		colorsByte[0] = new byte[] {0, 0, 0};
		for(int i = 0; i < colors.length; ++i) {
			colorsByte[i + 1] = new byte[] {(byte) colors[i].getRed(), (byte) colors[i].getGreen(), (byte) colors[i].getBlue()}; 
		}
		
		byte[][] colorsGoodOrientation = new byte[3][colorsByte.length];
		for(int i = 0; i < colorsByte.length; ++i) {
			for(int j = 0; j < colorsByte[i].length; ++j) {
				colorsGoodOrientation[j][i] = colorsByte[i][j];
			}
		}
		
		return colorsGoodOrientation;
	}

	public static LUT getColorsLUT(Color[] colors) {
		byte[][] b = getColorsBytes(colors);
		LUT lut = new LUT(8, colors.length + 1, b[0], b[1], b[2]);

		return lut;
	}

	public static ColorTable getColorsColorTable(Color[] colors) {
		byte[][] b = getColorsBytes(colors);
		ColorTable colorTable = new ColorTable8(b);

		return colorTable;
	}
	
}
