/*
 * Copyright 2020 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import cd.go.contrib.plugins.configrepo.groovy.dsl.GitMaterial
import cd.go.contrib.plugins.configrepo.groovy.dsl.GoCD

GoCD.script {
  branches {
    matching {
      from = github {
        fullRepoName = 'gocd/gocd'
        materialUrl = "https://git.gocd.io/git/${fullRepoName}"
      }

      onMatch { ctx ->
        // Build your entire workflow; you can have many pipeline blocks here.
        pipeline("build-linux-${ctx.branchSanitized}") {
          group = "gocd-${ctx.branchSanitized}"
          template = 'build-gradle-linux'
          materials { add(ctx.repo) }
          params = [OS: 'linux', BROWSER: 'firefox']
        }

        pipeline("build-windows-${ctx.branchSanitized}") {
          group = "gocd-${ctx.branchSanitized}"
          template = 'build-gradle-windows'
          materials { add(ctx.repo) }
          params = [OS: 'windows', BROWSER: 'msedge']
        }

        pipeline("plugins-${ctx.branchSanitized}") {
          group = "gocd-${ctx.branchSanitized}"
          template = 'plugins-gradle'

          materials {
            add((ctx.repo as GitMaterial).dup({
              destination = 'gocd'
            }))

            git('go-plugins') {
              url = 'https://git.gocd.io/git/gocd/go-plugins'
              shallowClone = true
              destination = 'go-plugins'
            }

            dependency('linux') {
              pipeline = "build-linux-${ctx.branchSanitized}"
              stage = 'build-server'
            }

            dependency('windows') {
              pipeline = "build-windows-${ctx.branchSanitized}"
              stage = 'build-server'
            }
          }
        }

        pipeline("installers-${ctx.branchSanitized}") {
          group = "gocd-${ctx.branchSanitized}"
          template = 'installers-gradle'

          materials {
            add((ctx.repo as GitMaterial).dup({
              destination = 'gocd'
            }))

            dependency('go-plugins') {
              pipeline = "plugins-${ctx.branchSanitized}"
              stage = 'build'
            }
          }
          environmentVariables = [
            UPDATE_GOCD_BUILD_MAP: 'Y',
            WINDOWS_64BIT_JDK_URL: 'https://nexus.gocd.io/repository/s3-mirrors/local/jdk/openjdk-11.0.2_windows-x64_bin.zip',
            WINDOWS_JDK_URL: 'https://nexus.gocd.io/repository/s3-mirrors/local/jdk/openjdk-11.0.2_windows-x64_bin.zip'
          ]
          params = ['plugins-pipeline-name': String.format("plugins-%s", ctx.branchSanitized)]
        }
      }
    }
  }
}
