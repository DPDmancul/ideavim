/*
 * Copyright 2003-2025 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */

package org.jetbrains.plugins.ideavim.ex.implementation.functions

import org.jetbrains.plugins.ideavim.VimTestCase
import org.junit.jupiter.api.Test


class SystemFunctionTest : VimTestCase() {
  @Test
  fun `simple echo shell`() {
    typeText(commandToKeys("""echo system('echo 1')"""))
    assertExOutput("1\n")
  }

  @Test
  fun `simple echo shell with escape`() {
    typeText(commandToKeys("""echo system('echo "\"1\""')"""))
    assertExOutput("\"1\"\n")
  }

  @Test
  fun `simple echo command`() {
    typeText(commandToKeys("""echo system(['echo', '1'])"""))
    assertExOutput("1\n")
  }

  @Test
  fun `env echo shell`() {
    typeText(commandToKeys("echo system('echo \$NOT_DEFINED_ENV')"))
    assertExOutput("\n")
  }

  @Test
  fun `env echo command`() {
    typeText(commandToKeys("echo system(['echo', '\$EUID'])"))
    assertExOutput("\$EUID\n")
  }

  @Test
  fun `missing parameter`() {
    typeText(commandToKeys("""echo system()"""))
    assertExOutput("E119: Not enough arguments for function: system")
  }

  @Test
  fun `empty command shell`() {
    typeText(commandToKeys("""echo system('')"""))
    assertExOutput("\n")
  }

  @Test
  fun `empty list command`() {
    typeText(commandToKeys("""echo system([])"""))
    assertExOutput("E474: Invalid argument")
  }

  @Test
  fun `empty command`() {
    typeText(commandToKeys("""echo system([''])"""))
    assertExOutput("E475: Invalid value for argument cmd: '' is not executable")
  }

  @Test
  fun `return status code`() {
    typeText(commandToKeys("""execute system('exit 123') | echo v:shell_error"""))
    assertExOutput("123")
  }

  @Test
  fun `string input with shell`() {
    typeText(commandToKeys("""echo system('cat', 'Lorem ipsum')"""))
    assertExOutput("Lorem ipsum\n")
  }

  @Test
  fun `string input without shell`() {
    typeText(commandToKeys("""echo system(['cat'], 'Lorem ipsum')"""))
    assertExOutput("Lorem ipsum\n")
  }

  @Test
  fun `list input`() {
    typeText(commandToKeys("""echo system('cat', ['Lorem', 'ipsum'])"""))
    assertExOutput("Lorem\nipsum\n")
  }
}