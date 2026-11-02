# Caciocavallo 1.18 (Java 17)

`cacio-shared` and `cacio-tta` jars, copied unchanged from PojavLauncher
(`app_pojavlauncher/src/main/assets/components/caciocavallo17`, commit
b12ad048157b3aa255d078c235dd4571e1900309). They are builds of the
caciocavallosilano fork of Caciocavallo, licensed GPLv2 with the Classpath
Exception.

They put AWT/Swing on the `com.github.caciocavallosilano.cacio.ctc.CTCToolkit`
toolkit. It renders every window into one offscreen screen buffer and takes
input through `CTCAndroidInput.receiveData`. `CTCScreen` loads
`libpojavexec_awt.so` from the first `$LD_LIBRARY_PATH` entry that contains it.
In this app that library is ours: `app/src/main/cpp/rlm_awt.c`.
