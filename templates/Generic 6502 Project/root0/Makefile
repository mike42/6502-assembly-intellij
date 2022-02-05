CA65=/usr/bin/ca65
LD65=/usr/bin/ld65
RUN6502=run6502

hello.img: hello.o hello.cfg
	$(LD65) --define sim_putchar=0xFFEE \
			 --define sim_gethar=0xFFEC \
			 --define sim_exit=0xFFEA \
			 -o hello.img \
			 -C hello.cfg \
			 hello.o

hello.o: hello.s
	$(CA65) hello.s

clean:
	rm -f hello.o hello.img

run: hello.img
	$(RUN6502) -l 8000 hello.img -R 8000 -P FFEE -G FFEC -X FFEA

