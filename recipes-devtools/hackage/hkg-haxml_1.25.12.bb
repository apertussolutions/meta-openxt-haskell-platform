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

# Library-only: Cabal appends --ghc-options after its inplace -package-db, so
# our target -clear-package-db drops the just-built HaXml unit and executable
# links fail. Dom0 only needs the library.
EXTRA_CABAL_CONF:append = " --disable-executables"
