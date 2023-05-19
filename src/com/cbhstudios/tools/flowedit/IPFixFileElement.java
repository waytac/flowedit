package com.cbhstudios.tools.flowedit;

public abstract class IPFixFileElement 
{
	public IPFixFileElement() { placementIndex = 0; };
	
	public Integer PlaceAt(Integer newPlacement)
	{
		placementIndex = newPlacement;
		return GetIndex();
	}
	
	public abstract Boolean Remove();

	public abstract Integer Read(IPFixStreamer ifs) throws FloweditException;
	public abstract Integer Write(IPFixStreamer ifs) throws FloweditException;
	public abstract Integer WrittenFileElementSize();
	
	public Integer GetIndex()	{ return placementIndex; }
	
	protected Integer placementIndex;
}
