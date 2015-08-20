/*

SuperCollider implementation of Dirt

Requires: sc3-plugins

Currently you can run only one instance at a time

Options:

* network port (default: 57120)
* vowelRegister (default: \tenor)



Open Qustions:

* should accelerate reduce the playback time? (currently: yes)
* should samples reverse when accelerate is negative and large? (currently: no)
* is accelerate direction relative to rate or absolute? (currently: absolute)

TODO:
* let's make a nice protocol of how to extend it better

One first step would be: Tidal sends pairs of argument names and values
Then we could map arguments to effects, and new effects could easity be added

*/

SuperDirt {

	var <numChannels, <server, <options;
	var <bus, <globalEffectBus;
	var <buffers;
	var <globalEffects;
	var <vowels;
	var <netResponders, <replyAddr;

	*new { |numChannels = 2, server, options|
		^super.newCopyArgs(numChannels, server ? Server.default, options ? ()).init
	}

	init {
		ServerTree.add(this, server); // synth node tree init
		globalEffects = ();
		buffers = ();
		this.initSynthDefs;
		this.initVowels(options[\vowelRegister] ? \tenor);
	}

	doOnServerTree {
		// on node tree init:
		this.initGlobalEffects
	}

	start {
		if(server.serverRunning.not) {
			Error("SuperColldier server '%' not running. Couldn't start SuperDirt".format(server.name)).warn;
			^this
		};
		this.initBusses;
		this.initGlobalEffects;
		this.openNetworkConnection(options[\port] ? 57120);
	}

	free {
		this.freeSoundFiles;
		this.closeNetworkConnection;
		this.freeBusses;
		ServerTree.remove(this, server);
	}

	initBusses {
		bus = Bus.audio(server, numChannels);
		globalEffectBus = Bus.audio(server, numChannels);
	}

	freeBusses {
		bus.free;
		globalEffectBus.free;
	}

	loadSoundFiles { |path, fileExtension = "wav"|
		var folderPaths;
		path = path ?? { "samples".resolveRelative };
		folderPaths = pathMatch(path +/+ "**");
		"loading sample banks:".postln;
		folderPaths.do { |folderPath|
			PathName(folderPath).filesDo { |filepath|
				var buf, name;
				if(filepath.extension.find(fileExtension, true).notNil) {
					buf = Buffer.read(server, filepath.fullPath);
					name = filepath.folderName.toLower;
					buffers[name.asSymbol] = buffers[name.asSymbol].add(buf)
				}
			};
			folderPath.basename.post; " ".post;
		};
		"\n\n".post;
	}

	freeSoundFiles {
		buffers.do { |x| x.asArray.do { |buf| buf.free } };
		buffers = ();
	}

	loadSynthDefs { |path|
		var filePaths;
		path = path ?? { "synths".resolveRelative };
		filePaths = pathMatch(path +/+ "*");
		filePaths.do { |filepath|
			if(filepath.splitext.last == "scd") {
				try { (dirt:this).use { filepath.load }; "loading synthdefs in %\n".postf(filepath) } { |err| err.postln };
			}
		}
	}

	initVowels { |register = \tenor|
		vowels = ();
		if(Vowel.formLib.at(\a).at(register).isNil) {
			"This voice register (%) isn't avaliable. Using tenor instead".format(register).warn;
			"Available registers are: %".format(Vowel.formLib.at(\a).keys).postln;
			register = \tenor;
		};

		[\a, \e, \i, \o, \u].collect { |x|
			vowels[x] = Vowel(x, register)
		};
	}

	initGlobalEffects {
		server.bind { // make sure they are in order
			[\dirt_limiter, \dirt_delay].do { |name|
				globalEffects[name] = Synth.after(1, name, [\out, 0, \effectBus, globalEffectBus]);
			}
		};
	}

	initSynthDefs {

		// global synth defs

		SynthDef(\dirt_delay, { |out, effectBus, delaytime, delayfeedback|
			var signal = In.ar(effectBus, numChannels);
			signal = SwitchDelay.ar(signal, 1, 1, delaytime, delayfeedback); // from sc3-plugins
			Out.ar(out, signal);
		}).add;

		SynthDef(\dirt_limiter, { |out|
			var signal = In.ar(out, numChannels);
			ReplaceOut.ar(signal, Limiter.ar(signal))
		}).add;


		SynthDef(\dirt, { |bufnum, startFrame, endFrame,
			pan = 0, amp = 0.1, speed = 1, accelerate = 0, keepRunning = 0|

			var sound, rate, phase, krPhase, endGate;

			// bufratescale adjusts the rate if sample doesn't have the same rate as soundcard
			rate = speed + Sweep.kr(rate: accelerate);

			// sample phase
			phase =  Sweep.ar(1, rate * BufSampleRate.kr(bufnum)) + startFrame;

			// release synth when end position is reached (or when backwards, start position)
			krPhase = A2K.kr(phase); // more efficient
			endGate = Select.kr(startFrame > endFrame, [
				InRange.kr(phase, startFrame, endFrame), // workaround
				InRange.kr(phase, endFrame, startFrame)
			]);
			endGate = endGate * (rate.abs > 0.0); // yes?

			sound = BufRd.ar(
				numChannels: 1, // mono samples only
				bufnum: bufnum,
				phase: phase,
				loop: 0, // should we loop?
				interpolation: 4 // cubic interpolation
			);


			this.panOut(sound, pan, amp * this.gateCutGroup(endGate));
		}).add;

		/*
		Add Effect SynthDefs
		*/
		/*

		The local effect synths are freed when input is silent for longer than 0.1 sec (in DetectSilence).
		This makes it unnecessary to keep track of any synths.
		But it may cause problems with samples that contain silence.

		One way to solve this involves bookkeeping of synths on the language side (haskell or sclang).
		For now, we use the simplest possible way.
		*/


		SynthDef(\dirt_vowel, { |out, cutoff = 440, resonance = 0.5, vowel, sustain = 1|
			var signal, vowelFreqs, vowelAmps, vowelRqs;
			signal = In.ar(out, numChannels);
			vowelFreqs = \vowelFreqs.ir(1000 ! 5) * (cutoff / 440);
			vowelAmps = \vowelAmps.ir(0 ! 5) * resonance.linlin(0, 1, 50, 350);
			vowelRqs = \vowelRqs.ir(0 ! 5) * resonance.linlin(0, 1, 1, 0.1);
			signal = BPF.ar(signal, vowelFreqs, vowelRqs, vowelAmps).sum;
			this.releaseWhenSilent(signal);
			ReplaceOut.ar(out, signal);

		}).add;

		// would be nice to have some more parameters in some cases

		SynthDef(\dirt_crush, { |out, crush = 4|
			var signal = In.ar(out, numChannels);
			this.releaseWhenSilent(signal);
			signal = signal.round(0.5 ** crush);
			ReplaceOut.ar(out, signal)
		}).add;


		SynthDef(\dirt_coarse, { |out, coarse = 0, bandq = 10|
			var signal = In.ar(out, numChannels);
			this.releaseWhenSilent(signal);
			signal = Latch.ar(signal, Impulse.ar(SampleRate.ir / coarse));
			ReplaceOut.ar(out, signal)
		}).add;

		SynthDef(\dirt_hpf, { |out, hcutoff = 440, hresonance = 0|
			var signal = In.ar(out, numChannels);
			signal = RHPF.ar(signal, hcutoff, hresonance.linexp(0, 1, 1, 0.001));
			this.releaseWhenSilent(signal);
			ReplaceOut.ar(out, signal)
		}).add;

		SynthDef(\dirt_bpf, { |out, bandqf = 440, bandq = 10|
			var signal = In.ar(out, numChannels);
			signal = BPF.ar(signal, bandqf, 1/bandq) * max(bandq, 1.0);
			this.releaseWhenSilent(signal);
			ReplaceOut.ar(out, signal)
		}).add;

		// the monitor does the mixing and zeroing of the busses for each sample grain

		SynthDef(\dirt_monitor, { |out, in, delayBus, delay = 0|
			var signal = In.ar(in, numChannels);
			this.releaseWhenSilent(signal);
			Out.ar(out, signal);
			Out.ar(delayBus, signal * delay);
			ReplaceOut.ar(in, Silent.ar(numChannels)) // clears bus signal for subsequent synths
		}).add;

	}


	/*
	convenience methods for panning and releasing
	*/

	panOut { |signal, pan = 0.0, mul = 1.0|
		var output;

		if(numChannels == 2) {
			output = Pan2.ar(signal, (pan * 2) - 1, mul)
		} {
			output = PanAz.ar(numChannels, signal, pan, mul)
		};
		if(signal.size > 1) { output = output.sum };

		^OffsetOut.ar(\out.kr, output); // we create an out control argument in a different way here.
	}

	releaseWhenSilent { |signal|
		DetectSilence.ar(LeakDC.ar(signal.asArray.sum), doneAction:2);
	}


	/*
	In order to avoid bookkeeping on the language side, we implement cutgroups as follows:
	The language initialises the synth with its sample id (some number that correlates with the sample name) and the cutgroup.
	Before we start the new synth, we send a /set message to all synths, and those that match the specifics will be released.
	*/

	gateCutGroup { |gate = 1|
		// this is necessary because the message "==" tests for objects, not for signals
		var same = { |a, b| BinaryOpUGen('==', a, b) };
		var sameCutGroup = same.(\cutGroup.kr(0), abs(\gateCutGroup.kr(0)));
		var sameSample = same.(\sample.kr(0), \gateSample.kr(0));
		var which = \gateCutGroup.kr(0).sign; // -1, 0, 1
		var free = Select.kr(which + 1, // 0, 1, 2
			[
				sameSample,
				0.0, // default cut group 0 doesn't ever cut
				1.0
			]
		) * sameCutGroup; // same cut group is mandatory

		^EnvGen.kr(Env.asr(0, 1, 0.01), (1 - free) * gate, doneAction:2);
	}


	sendSynth { |instrument, args|
		server.sendMsg(\s_new, instrument,
			-1, // no id
			1, // add action: addToTail
			1, // send to group 1
			*args.asOSCArgArray // append all other args
		)
	}


	/*
	This uses the Dirt OSC API
	*/

	value2 { |args| // args are in the shape [key, val, key, val ...]
		this.performKeyValuePairs(\value, args)
	}

	value {
		|latency, cps = 1, name, offset = 0, start = 0, end = 1, speed = 1, pan = 0, velocity,
		vowel, cutoff = 300, resonance = 0.5,
		accelerate = 0, shape, krio, gain = 1, cutgroup = 0,
		delay = 0, delaytime = 0, delayfeedback = 0,
		crush = 0,
		coarse = 0,
		hcutoff = 0, hresonance = 0,
		bandqf = 0, bandq = 0,
		unit = \r|

		var amp, allbufs, buffer, group;
		var instrument, key, index, sample;
		var temp;
		var length, sampleRate, numFrames, bufferDuration;
		var sustain, startFrame, endFrame;

		#key, index = name.asString.split($:);
		key = key.asSymbol;
		allbufs = this.buffers[key];
		index = (index ? 0).asInteger;


		if(allbufs.notNil or: { SynthDescLib.at(key).notNil }) {

			if(allbufs.notNil) {
				instrument = \dirt;
				buffer = allbufs.wrapAt(index);
				numFrames = buffer.numFrames;
				bufferDuration = buffer.duration;
				sampleRate = buffer.sampleRate;
				sample = name.identityHash;
			} {
				instrument = key;
				sampleRate = server.sampleRate;
				numFrames = sampleRate; // assume one second
				bufferDuration = 1.0;
			};

			if(end >= start) {
				if(speed < 0) { temp = end; end = start; start = temp };
				length = end - start;
			} {
				// backwards
				length = start - end;
				speed = speed.neg;
			};


			unit = unit ? \r;
			amp = pow(gain, 4);

			// sustain is the duration of the sample
			switch(unit,
				\r, {
					sustain = bufferDuration * length / speed;
					startFrame = numFrames * start;
					endFrame = numFrames * end;
				},
				\c, {
					sustain = length / cps;
					speed = speed * cps;
					startFrame = numFrames * start;
					endFrame = numFrames * end;
				},
				\s, {
					sustain = length;
					startFrame = sampleRate * start;
					endFrame = sampleRate * end;
				}
			);

			//[\end_start, endFrame - startFrame / sampleRate, \sustain, sustain].postln;

			if(accelerate != 0) {
				// assumes linear acceleration
				sustain = sustain + (accelerate * sustain * 0.5 * speed.sign.neg);
			};


			server.makeBundle(latency, { // use this to build a bundle

				if(cutgroup != 0) {
					// set group 1, in which all synths are living
					server.sendMsg(\n_set, 1, \gateCutGroup, cutgroup, \gateSample, sample);
				};

				// set global delay synth parameters
				if(delaytime != 0 or: { delayfeedback != 0 }) {
					server.sendMsg(\n_set, globalEffects[\dirt_delay].nodeID,
						\delaytime, delaytime,
						\delayfeedback, delayfeedback
					);
				};

				this.sendSynth(instrument, [
					sustain: sustain,
					speed: speed,
					bufnum: buffer,
					start: start,
					end: end,
					startFrame: startFrame,
					endFrame: endFrame,
					pan: pan,
					accelerate: accelerate,
					amp: amp,
					offset: offset,
					cutGroup: cutgroup.abs, // ignore negatives here!
					sample: sample,
					cps: cps,
					out: bus]
				);

				if(vowel.notNil) {
					vowel = vowels[vowel];
					if(vowel.notNil) {
						this.sendSynth(\dirt_vowel,
							[
								out: bus,
								vowelFreqs: vowel.freqs,
								vowelAmps: vowel.amps,
								vowelRqs: vowel.rqs,
								cutoff: cutoff,
								resonance: resonance,
								sustain: sustain
							]
						);
					}

				};

				if(hcutoff != 0) {
					this.sendSynth(\dirt_hpf,
						[
							hcutoff: hcutoff,
							hresonance: hresonance,
							out: bus
						]
					)
				};

				if(bandqf != 0) {
					this.sendSynth(\dirt_bpf,
						[
							bandqf: bandqf,
							bandq: bandq,
							out: bus
						]
					)
				};

				if(crush != 0) {
					this.sendSynth(\dirt_crush,
						[
							crush: crush,
							out: bus
						]
					)
				};

				if(coarse > 1) { // coarse == 1 => full rate
					this.sendSynth(\dirt_coarse,
						[
							coarse: coarse,
							out: bus
						]
					)
				};


				this.sendSynth(\dirt_monitor,
					[
						in: bus,  // read from private
						out: 0,     // write to public,
						delayBus: globalEffectBus,
						delay: delay
					]
				);


			});

		} {
			"Dirt: no sample or instrument found for this name: %\n".postf(name);
		}
	}

	openNetworkConnection { |port = 57120|

		this.closeNetworkConnection;

		// current standard protocol

		netResponders.add(

			OSCFunc({ |msg, time, tidalAddr|
				var latency = time - Main.elapsedTime;
				if(latency > 2) {
					"The scheduling delay is too long. Your networks clocks may not be in sync".warn;
					latency = 0.2;
				};
				replyAddr = tidalAddr; // collect tidal reply address
				this.value(latency, *msg[1..]);
			}, '/play', recvPort: port).fix

		);

		netResponders.add(
			// an alternative protocol, uses pairs of parameter names and values in arbitrary order
			OSCFunc({ |msg, time, tidalAddr|
				var latency = time - Main.elapsedTime;
				if(latency > 2) {
					"The scheduling delay is too long. Your networks clocks may not be in sync".warn;
					latency = 0.2;
				};
				replyAddr = tidalAddr; // collect tidal reply address
				this.value2([\latency, latency] ++ msg[1..]);
			}, '/play2', recvPort: port).fix
		);

		"SuperDirt: listening to Tidal on port %".format(port).postln;
	}

	closeNetworkConnection {
		netResponders.do { |x| x.free };
		netResponders = List.new;
	}

	sendToTidal { |args|
		if(replyAddr.notNil) {
			replyAddr.sendMsg(*args);
		} {
			"Currently no connection to tidal".warn;
		}
	}


}