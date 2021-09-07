#! /usr/bin/env bash

: ${git_root:="$(git rev-parse --show-toplevel || true)"}
: ${mvn_basedir:="${git_root:?}"}
: ${input_file:="${mvn_basedir}/target/aocr-jar-with-dependencies.jar"}
: ${output_file:="${input_file%-jar-with-dependencies.jar}"}

: ${scriptdir:="${mvn_basedir}/etc/scripts"}
: ${prefix_file:="${scriptdir}/java-bin-prefix"}

cat "${prefix_file}" "${input_file}" > "${output_file}"
chmod +x "${output_file}"
