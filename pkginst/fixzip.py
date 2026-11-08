import zipfile,sys
src,dst=sys.argv[1],sys.argv[2]
zi=zipfile.ZipFile(src); zo=zipfile.ZipFile(dst,'w')
for i in zi.infolist():
    d=zi.read(i.filename)
    t=zipfile.ZIP_STORED if i.filename=='resources.arsc' or i.filename.endswith('.so') else zipfile.ZIP_DEFLATED
    zo.writestr(zipfile.ZipInfo(i.filename,(2020,1,1,0,0,0)),d,compress_type=t)
zo.close()
