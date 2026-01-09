# WAD Format Notes

WAD header:
- 4 bytes: IWAD/PWAD
- 4 bytes: number of lumps
- 4 bytes: directory offset

Directory Entry:
- 4 bytes: filepos
- 4 bytes: size
- 8 bytes: name

Map lumps order:
THINGS
LINEDEFS
SIDEDEFS
VERTEXES
SEGS
SSECTORS
NODES
SECTORS
REJECT
BLOCKMAP

We only care about:
VERTEXES
LINEDEFS
SIDEDEFS
SECTORS
THINGS
