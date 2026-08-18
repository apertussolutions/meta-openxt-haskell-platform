# This recipe only provides the runtime libraries for the *target*.
# installed-vs-shipped set to silence everything not packaged on purpose.

require ghc-${PV}.inc

PACKAGES = " \
    ${PN} \
"
FILES:${PN} = " \
    ${libdir}/ghc-${PV}/array-*/*.so \
    ${libdir}/ghc-${PV}/base-*/*.so \
    ${libdir}/ghc-${PV}/binary-*/*.so \
    ${libdir}/ghc-${PV}/bytestring-*/*.so \
    ${libdir}/ghc-${PV}/containers-*/*.so \
    ${libdir}/ghc-${PV}/deepseq-*/*.so \
    ${libdir}/ghc-${PV}/directory-*/*.so \
    ${libdir}/ghc-${PV}/filepath-*/*.so \
    ${libdir}/ghc-${PV}/ghc-boot-th-*/*.so \
    ${libdir}/ghc-${PV}/ghc-prim-*/*.so \
    ${libdir}/ghc-${PV}/haskell98-*/*.so \
    ${libdir}/ghc-${PV}/integer-gmp-*/*.so \
    ${libdir}/ghc-${PV}/integer-simple-*/*.so \
    ${libdir}/ghc-${PV}/libHSffi-ghc${PV}.so \
    ${libdir}/ghc-${PV}/mtl-*/*.so \
    ${libdir}/ghc-${PV}/old-locale-*/*.so \
    ${libdir}/ghc-${PV}/old-time-*/*.so \
    ${libdir}/ghc-${PV}/parsec-*/*.so \
    ${libdir}/ghc-${PV}/pretty-*/*.so \
    ${libdir}/ghc-${PV}/process-*/*.so \
    ${libdir}/ghc-${PV}/random-*/*.so \
    ${libdir}/ghc-${PV}/rts/libHSrts-ghc${PV}.so \
    ${libdir}/ghc-${PV}/rts/libHSrts_debug-ghc${PV}.so \
    ${libdir}/ghc-${PV}/rts/libHSrts_thr-ghc${PV}.so \
    ${libdir}/ghc-${PV}/rts/libHSrts_thr_debug-ghc${PV}.so \
    ${libdir}/ghc-${PV}/stm-*/*.so \
    ${libdir}/ghc-${PV}/syb-*/*.so \
    ${libdir}/ghc-${PV}/template-haskell-*/*.so \
    ${libdir}/ghc-${PV}/text-*/*.so \
    ${libdir}/ghc-${PV}/time-*/*.so \
    ${libdir}/ghc-${PV}/transformers-*/*.so \
    ${libdir}/ghc-${PV}/unix-*/*.so \
    ${libdir}/ghc-${PV}/utf8-string-*/*.so \
"
# ldflags: GHC shared objects omit GNU_HASH under our link flags.
# file-rdeps: RTS links libffi; shlib dependency resolution is brittle
# across libffi SONAME bumps — declare RDEPENDS and skip the QA noise.
INSANE_SKIP:${PN} = "installed-vs-shipped ldflags file-rdeps"
RDEPENDS:${PN} += "libffi"

DEPENDS += " \
    ghc-native \
    gmp \
    libffi \
    ncurses \
"
BUILD_RANLIB:remove = "-D"

# In-tree GMP 6.2.1 configure fails under GCC 15 / C23 (empty () = void).
# Use the target gmp already in the sysroot instead.
# Also serialize make: parallel library configures race on shared stamp files.
PARALLEL_MAKE = ""

# GHC passes SettingsCCompilerCommand to posix_spawnp as a *single* program
# name. OE's ${CC} is multi-word ("x86_64-oe-linux-gcc -m64 … --sysroot=…"),
# so we install single-word wrappers that expand to the full OE tool lines.
#
# Previously this recipe used host `gcc`/`ld`, which built an RTS that
# referenced host glibc symbols (__isoc23_strtol on Debian Trixie) missing
# from target glibc 2.31.
do_configure() {
    install -d ${WORKDIR}/wrappers
    cat > ${WORKDIR}/wrappers/target-cc <<EOF
#! /bin/sh
exec ${CC} "\$@"
EOF
    cat > ${WORKDIR}/wrappers/target-ld <<EOF
#! /bin/sh
exec ${LD} "\$@"
EOF
    cat > ${WORKDIR}/wrappers/host-cc <<EOF
#! /bin/sh
exec ${BUILD_CC} ${BUILD_CFLAGS} "\$@"
EOF
    chmod 0755 ${WORKDIR}/wrappers/target-cc \
               ${WORKDIR}/wrappers/target-ld \
               ${WORKDIR}/wrappers/host-cc

    ./configure --prefix=${prefix} \
                --host=${BUILD_SYS} \
                --build=${BUILD_SYS} \
                --target=${TARGET_SYS} \
                --with-gmp-includes=${STAGING_INCDIR} \
                --with-gmp-libraries=${STAGING_LIBDIR} \
                CC="${WORKDIR}/wrappers/target-cc" \
                CC_STAGE0="${WORKDIR}/wrappers/host-cc" \
                LD="${WORKDIR}/wrappers/target-ld" \
                AR="${AR}" \
                NM="${NM}" \
                RANLIB="${RANLIB}" \
                STRIP="${STRIP}"

    # Sysroot is already embedded in the target-cc wrapper (${CC}). Do not
    # put --sysroot into STANDARD_OPTS: GHC passes those as its own flags
    # (not only -optc), which breaks ghc-stage1.
    #
    # Append C/LD flags to UNPACKDIR/build.mk (not ${S}/mk/build.mk):
    # ghc-8.10.7.inc's do_compile:prepend re-copies UNPACKDIR/build.mk into
    # ${S}/mk/ and would wipe any post-copy append in do_compile:prepend.
    cat >> ${UNPACKDIR}/build.mk <<EOF
# OE: bootstrap (host) and target C/LD flags for ABI-correct RTS.
# -std=gnu17: GHC 8.10 C sources (e.g. utils/hp2ps) use K&R
# "extern void *realloc();" which is (void) under GCC 15 / C23.
CONF_CC_OPTS_STAGE0 += ${BUILD_CFLAGS} -std=gnu17
CONF_CC_OPTS_STAGE1 += ${CFLAGS} -std=gnu17
CONF_CC_OPTS_STAGE2 += ${CFLAGS} -std=gnu17
CONF_GCC_LINKER_OPTS_STAGE1 += ${LDFLAGS}
CONF_GCC_LINKER_OPTS_STAGE2 += ${LDFLAGS}
EOF
}

# hp2ps uses K&R "extern void *malloc();" which is (void) under C23.
# Strip those declarations so <stdlib.h> prototypes are used (also covered
# by -std=gnu17 in build.mk, but keep this for hosts defaulting to C23).
do_compile:prepend() {
    local u="${S}/utils/hp2ps/Utilities.c"
    if [ -f "$u" ]; then
        grep -q '#include <stdlib.h>' "$u" || \
            sed -i '/#include <string.h>/a #include <stdlib.h>' "$u"
        sed -i \
            -e '/^extern void\* malloc();$/d' \
            -e '/^[[:space:]]*extern void \*realloc();$/d' \
            "$u"
    fi
}

# Fail early if the RTS was still built against a newer *host* libc than
# the target. On Dunfell-era targets (glibc 2.31) __isoc23_* was host-only.
# Wrynose target glibc 2.43 exports those symbols, so skip the check when
# the sysroot libc already provides them.
do_install:append() {
    local rts_a="${D}${libdir}/ghc-${PV}/rts/libHSrts.a"
    local libc
    for libc in "${STAGING_DIR_TARGET}/lib/libc.so.6" \
                "${STAGING_DIR_TARGET}/lib64/libc.so.6" \
                "${STAGING_DIR_TARGET}/usr/lib/libc.so.6"; do
        [ -e "$libc" ] || continue
        if nm -D "$libc" 2>/dev/null | grep -q '__isoc23_'; then
            bbnote "Target libc provides __isoc23_*; skipping host-glibc RTS check"
            return 0
        fi
        break
    done
    if [ -f "${rts_a}" ] && nm "${rts_a}" 2>/dev/null | grep -q '__isoc23_'; then
        bbfatal "ghc-runtime RTS (${rts_a}) references host-only glibc symbols (__isoc23_*). Target toolchain was not used for RTS C objects."
    fi
}

do_sysroot_ghc_pkg_db() {
    ghc-pkg recache
}
addtask do_sysroot_ghc_pkg_db before do_configure after do_prepare_recipe_sysroot
do_sysroot_ghc_pkg_db[doc] = "Amend the paths in the package database local to the recipes' sysroot."
do_sysroot_ghc_pkg_db[dirs] = "${B}"
