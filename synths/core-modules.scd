/*

CORE MODULES
(REQUiRE: CORE SYNTHS)

Core Modules: these are called for each event from tidal,
and they represent the list of possible (effect) synths on the event level

Usually, they correspond to a SynthDef (here in core-synths), you could call any function in them
They will be called when the test is fulfilled (e.g. ~vowel.notNil)

You can't currently add your own, because Tidal needs to support this extension first.

*/



/*
The sound source to be filtered by later stages (modules)

This could be:

- a sample that is played
- a generic synth, which allows us to embed a SynthDef name in a tidal pattern next to sample names

A SynthDef could receivb any number of the parameters in the event, e.g.
sustain, speed, endSpeed, begin, end, pan, accelerate, offset, cps

it ought to use "out" as bus output argument.

the diversion is a hook to add an arbitrary function instead of a synth def.
this may be refacored later.
*/
(

~dirt.addModule('sound',
	{ |dirtEvent|
		var instrument, args;
		if(~diversion.value.isNil) {
			if(~buffer.notNil) {
				// arguments could be omitted using getMsgFunc, but for making it easier to understand, we write them out
				args = [
					bufnum: ~buffer,
					sustain: ~sustain,
					speed: ~speed,
					freq: ~freq * ~metaDataTuneRatio.value,
					endSpeed: ~endSpeed,
					begin: ~begin,
					end: ~end,
					pan: ~pan,
          base: ~base,
          width: ~width,
					out: ~out
				];

				if(~timescale.notNil) {
					instrument = ~stretchInstrument;
					args = args ++ [\timescale, ~timescale, \timescalewin, ~timescalewin ? 1];
				} {
					instrument = ~instrument;
				};

				dirtEvent.sendSynth(instrument, args);

			} {
				if(~playInside.isNil) {
					if(~instrument.isNil) {
						"module 'sound': instrument not found: %".format(~s).postln
					} {
						// here, we just derive the arguments as necessary from the environment
						dirtEvent.sendSynth(~instrument, ~argNames)
					}
				} {
					~playInside.(dirtEvent)
				}
			}
		}
});


~dirt.addModule('vowel',
	{ |dirtEvent|
		var vowel = ~dirt.vowels[~vowel];
		if(vowel.notNil) {
			dirtEvent.sendSynth("dirt_vowel" ++ ~numChannels,
				[
					out: ~out,
					vowelFreqs: [vowel.freqs],
					vowelAmps: [vowel.amps],
					vowelRqs: [vowel.rqs],
					resonance: ~resonance,
				]
			)
		}

}, { ~vowel.notNil });


~dirt.addModule('shape',
	{ |dirtEvent|
		dirtEvent.sendSynth("dirt_shape" ++ ~numChannels,
			[
				shape: ~shape,
				out: ~out
		])
}, { ~shape.notNil });


~dirt.addModule('tape',
	{ |dirtEvent|
		dirtEvent.sendSynth("dirt_analog_tape" ++ ~numChannels,
			[
				saturation: ~tape,
        tapeDrive: ~tapeDrive,
        bias: ~bias,
        oversample: ~oversample,
				out: ~out
		])
}, { ~tape.notNil });

~dirt.addModule('hpf',
	{ |dirtEvent|
		dirtEvent.sendSynth("dirt_hpf" ++ ~numChannels,
			[
				hcutoff: ~hcutoff,
				hresonance: ~hresonance,
				out: ~out,
        fattack: ~fattack,
        fdecay: ~fdecay,
        sweep: ~sweep,
		])
}, { ~hcutoff.notNil });

~dirt.addModule('bpf',
	{ |dirtEvent|
		dirtEvent.sendSynth("dirt_bpf" ++ ~numChannels,
			[
				bandqf: ~bandf,
				bandq: ~bandq,
				out: ~out,
        fattack: ~fattack,
        fdecay: ~fdecay,
        sweep: ~sweep,
		])

}, { ~bandf.notNil });

~dirt.addModule('crush',
	{ |dirtEvent|
		dirtEvent.sendSynth("dirt_crush" ++ ~numChannels,
			[
				crush: ~crush,
				out: ~out
		])

}, { ~crush.notNil });


~dirt.addModule('coarse',
	{ |dirtEvent|
		dirtEvent.sendSynth("dirt_coarse" ++ ~numChannels,
			[
				coarse: ~coarse,
				out: ~out
		])

}, { ~coarse.notNil and: { ~coarse > 1 } }); // coarse == 1 => full rate

~dirt.addModule('lpf',
	{ |dirtEvent|
		dirtEvent.sendSynth("dirt_lpf" ++ ~numChannels,
			[
				cutoff: ~cutoff,
				resonance: ~resonance,
				out: ~out,
        sweep: ~sweep,
        fattack: ~fattack,
        fdecay: ~fdecay,
		])
}, { ~cutoff.notNil });

// Vadim LPF 2-pole
~dirt.addModule('vlpf2',
  { |dirtEvent|
    dirtEvent.sendSynth("dirt_vadim_lpf_2" ++ ~numChannels,
      [
        freq: ~vlpf2, // Using freq to directly set cutoff frequency
        resonance: ~resonance,
        out: ~out,
        sweep: ~sweep,
        fattack: ~fattack,
        fdecay: ~fdecay,
      ])
}, { ~vlpf2.notNil });

// Vadim LPF 4-pole
~dirt.addModule('vlpf4',
  { |dirtEvent|
    dirtEvent.sendSynth("dirt_vadim_lpf_4" ++ ~numChannels,
      [
        freq: ~vlpf4, // Using freq for cutoff frequency
        resonance: ~resonance,
        out: ~out,
        sweep: ~sweep,
        fattack: ~fattack,
        fdecay: ~fdecay,
      ])
}, { ~vlpf4.notNil });

// Vadim BPF 2-pole
~dirt.addModule('vbpf2',
  { |dirtEvent|
    dirtEvent.sendSynth("dirt_vadim_bpf_2" ++ ~numChannels,
      [
        freq: ~vbpf2, // Using freq for center frequency
        resonance: ~resonance,
        out: ~out,
        sweep: ~sweep,
        fattack: ~fattack,
        fdecay: ~fdecay,
      ])
}, { ~vbpf2.notNil });

// Vadim BPF 4-pole
~dirt.addModule('vbpf4',
  { |dirtEvent|
    dirtEvent.sendSynth("dirt_vadim_bpf_4" ++ ~numChannels,
      [
        freq: ~vbpf4, // Using freq for center frequency
        resonance: ~resonance,
        out: ~out,
        sweep: ~sweep,
        fattack: ~fattack,
        fdecay: ~fdecay,
      ])
}, { ~vbpf4.notNil });

// Vadim HPF 2-pole
~dirt.addModule('vhpf2',
  { |dirtEvent|
    dirtEvent.sendSynth("dirt_vadim_hpf_2" ++ ~numChannels,
      [
        freq: ~vhpf2, // Using freq for cutoff frequency
        resonance: ~resonance,
        out: ~out,
        sweep: ~sweep,
        fattack: ~fattack,
        fdecay: ~fdecay,
      ])
}, { ~vhpf2.notNil });

// Vadim HPF 4-pole
~dirt.addModule('vhpf4',
  { |dirtEvent|
    dirtEvent.sendSynth("dirt_vadim_hpf_4" ++ ~numChannels,
      [
        freq: ~vhpf4, // Using freq for cutoff frequency
        resonance: ~resonance,
        out: ~out,
        sweep: ~sweep,
        fattack: ~fattack,
        fdecay: ~fdecay,
      ])
}, { ~vhpf4.notNil });

~dirt.addModule('pshift',
	{ |dirtEvent|
		dirtEvent.sendSynth('dirt_pitch_shift' ++ ~numChannels,
			[
				psrate: ~psrate,
				psdisp: ~psdisp,
				sustain: ~sustain,
				out: ~out
		])
}, { ~psrate.notNil });

~dirt.addModule('envelope',
	{ |dirtEvent|
		dirtEvent.sendSynth('dirt_envelope' ++ ~numChannels,
			[
				attack: ~attack,
				hold: ~hold,
				release: ~release,
				curve: ~curve,
				out: ~out
		])
}, { ~attack.notNil or: { ~release.notNil }});

~dirt.addModule('grenvelo',
	{ |dirtEvent|
		dirtEvent.sendSynth('dirt_grain_envelope' ++ ~numChannels,
			[
				sustain: ~sustain,
				tilt: ~tilt,
				plat: ~plat,
				curve:~curve,
				out: ~out
		])
}, { ~tilt.notNil });


~dirt.addModule('tremolo',
	{ |dirtEvent|
		dirtEvent.sendSynth('dirt_tremolo' ++ ~numChannels,
			[
				tremolorate: ~tremolorate,
				tremolodepth: ~tremolodepth,
				out: ~out
		])
}, { ~tremolorate.notNil });

// Phaser audio DSP effect declaration
~dirt.addModule('phaser',
	{ |dirtEvent|
		dirtEvent.sendSynth('dirt_phaser' ++ ~numChannels,
			[
				phaserrate: ~phaserrate,
				phaserdepth: ~phaserdepth,
				out: ~out
		])
}, { ~phaserrate.notNil });

);
