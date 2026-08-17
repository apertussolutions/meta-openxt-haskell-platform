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
INSANE_SKIP:${PN} = "installed-vs-shipped"

DEPENDS += " \
    ghc-native \
    libffi \
    ncurses \
"
BUILD_RANLIB:remove = "-D"

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
                --with-intree-gmp \
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
# OE: bootstrap (host) and target C/LD flags for ABI-correct RTS
CONF_CC_OPTS_STAGE0 += ${BUILD_CFLAGS}
CONF_CC_OPTS_STAGE1 += ${CFLAGS}
CONF_CC_OPTS_STAGE2 += ${CFLAGS}
CONF_GCC_LINKER_OPTS_STAGE1 += ${LDFLAGS}
CONF_GCC_LINKER_OPTS_STAGE2 += ${LDFLAGS}
EOF
}

# Fail early if the RTS was still built against a newer host libc.
do_install:append() {
    local rts_a="${D}${libdir}/ghc-${PV}/rts/libHSrts.a"
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
