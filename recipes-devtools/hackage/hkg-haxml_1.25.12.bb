DESCRIPTION = "Haskell utilities for parsing, filtering, transforming and generating XML documents."
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://COPYRIGHT;md5=fe41c6f999f87439d72ea3f6f16d7167"

HPN = "HaXml"
inherit hackage

SRC_URI[md5sum] = "4027c26251cc3f0533795e902dfef6d5"
SRC_URI[sha256sum] = "ec61ac2b33cf5032082ebaa109daea1ffa03baae090c0cc04624f19642b958f5"

DEPENDS += "\
    hkg-polyparse \
    hkg-random \
"

# Library-only on target: Cabal appends --ghc-options after its inplace
# -package-db, so -clear-package-db drops the just-built HaXml unit and
# executable links fail. Dom0 only needs the library.
do_compile:class-target() {
    if [ "${DISABLE_GHC_WRAPPERS}" != "1" ]; then
        haskell_write_oe_wrappers
    fi
    ${RUNGHC} Setup.*hs build lib:${HPN} --verbose
}

do_install:class-target() {
    ${RUNGHC} Setup.*hs copy lib:${HPN} --destdir="${D}" --verbose

    # Same package.conf.d install as haskell.bbclass do_install (override
    # replaces the whole function).
    if [ -f "${B}/${HPN}-${HPV}.conf" ]; then
        ghc_version=$(get_ghc_version)
        install -m 755 -d ${D}${libdir}/ghc-${ghc_version}/package.conf.d
        install -m 644 ${B}/${HPN}-${HPV}.conf ${D}${libdir}/ghc-${ghc_version}/package.conf.d
    fi
}
