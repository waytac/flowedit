/**
 * 
 */
package com.cbhstudios.tools.flowedit;

/**
 * @author wtackabury
 *
 */
public class FloweditException extends Exception {
	public static final long serialVersionUID = 1L;
	
	FloweditException(String exceptionText, Exception enclosingException)
	{
		super("Flowedit Exception : " + exceptionText + ":\n  " + enclosingException.getMessage());
	}
	
	FloweditException(String exceptionText)
	{
		super("Flowedit Exception : " + exceptionText);
	}
}
