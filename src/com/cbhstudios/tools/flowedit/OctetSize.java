package com.cbhstudios.tools.flowedit;

public class OctetSize {
	// I initially made the mistake of thinking 128 bits was "double".  Not only is it not intended for
	// 128 bit numerics, but java.lang.Double is in fact intended for IEEE-754 float encoding to high
	// precision.
	// Need something more specfic for, e.g., timestamps and IPv6 addresses.
	public static int LongLong() {return Long() * 2; };
	public static int Long() {return Long.SIZE/Byte.SIZE; };
	public static int Integer() {return Integer.SIZE/Byte.SIZE; };
	public static int Short() {return Short.SIZE/Byte.SIZE; };
	public static int Float() { return Float.SIZE/Byte.SIZE; };
	public static int Byte() {return 1; };
	public static int Octet() { return Byte(); };
}
