/*
 * Copyright 2003-2025 The IdeaVim authors
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE.txt file or at
 * https://opensource.org/licenses/MIT.
 */

package com.maddyhome.idea.vim.vimscript.model.functions.handlers

import com.intellij.vim.annotations.VimscriptFunction
import com.maddyhome.idea.vim.api.ExecutionContext
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.globalOptions
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.diagnostic.vimLogger
import com.maddyhome.idea.vim.ex.ExException
import com.maddyhome.idea.vim.vimscript.model.VimLContext
import com.maddyhome.idea.vim.vimscript.model.datatypes.VimDataType
import com.maddyhome.idea.vim.vimscript.model.datatypes.VimInt
import com.maddyhome.idea.vim.vimscript.model.datatypes.VimList
import com.maddyhome.idea.vim.vimscript.model.datatypes.VimString
import com.maddyhome.idea.vim.vimscript.model.expressions.Expression
import com.maddyhome.idea.vim.vimscript.model.functions.FunctionHandler
import java.io.File
import java.io.IOException

/**
 * Implementation of Vim's system() function.
 * Gets the output of the execution of the system command provided as first argument.
 * The command could be either a string (executed in shell) or a list.
 * Example: system('echo $EUID') returns '1000'
 * Example: system(['echo', '$EUID']) returns '$EUID'
 * With the optional second parameter one can specify the stdin for the executed command.
 * The command exit code is saved in the shell_error variable.
 * Example: :execute system('exit 123') | echo v:shell_error  prints 123
 */
@VimscriptFunction(name = "system")
internal class SystemFunctionHandler : FunctionHandler() {
  private val logger = vimLogger<SystemFunctionHandler>()

  override val minimumNumberOfArguments = 1
  override val maximumNumberOfArguments = 2

  override fun doFunction(
    argumentValues: List<Expression>,
    editor: VimEditor,
    context: ExecutionContext,
    vimContext: VimLContext,
  ): VimDataType {
    val cmd = argumentValues[0].evaluate(editor, context, vimContext)
    val input = argumentValues.getOrNull(1)?.let {
      when (val value = it.evaluate(editor, context, vimContext)) {
        is VimList -> value.values.joinToString("\n") { it.asString().replace('\n', '\u0000') }
        is VimInt -> injector.file.getEditor(value.value, context)
          ?.text()
          ?.toString()
          ?: throw ExException("E86: Buffer ${value.value} does not exist")
        else -> value.asString()
      }
    }

    //val project = PlatformDataKeys.PROJECT.getData(context.context as DataContext)
    val workingDirectory = null //project?.basePath

    val (statusCode, output) = when (cmd) {
      is VimList -> executeCommand(cmd.values.map { it.asString() }, input, workingDirectory)
      else -> executeInShell(cmd.asString(), input, workingDirectory)
    }

    injector.variableService.storeVimVariable("shell_error", VimInt(statusCode))

    return VimString(
      output
        .replace("\r\n", "\n")       // replace <CR><NL> with <NL>
        .replace('\u0000', '\u0001') // replace NUL with SOH
    )
  }

  private fun executeInShell(
    command: String,
    input: String?,
    currentDirectoryPath: String?,
  ): Pair<Int, String> {
    val shell = injector.globalOptions().shell
    val shellcmdflag = injector.globalOptions().shellcmdflag
    val shellxescape = injector.globalOptions().shellxescape
    val shellxquote = injector.globalOptions().shellxquote

    // For Win32. See :help 'shellxescape'
    val escapedCommand = if (shellxquote == "(") doEscape(command, shellxescape, "^")
    else command
    // Required for Win32+cmd.exe, defaults to "(". See :help 'shellxquote'
    val quotedCommand = if (shellxquote == "(") "($escapedCommand)"
    else (if (shellxquote == "\"(") "\"($escapedCommand)\""
    else shellxquote + escapedCommand + shellxquote)

    val commands = ArrayList<String>().apply {
      add(shell)
      addAll(shellcmdflag.split(' '))
      add(quotedCommand)
    }

    return executeCommand(commands, input, currentDirectoryPath)
  }

  private fun executeCommand(
    command: List<String>,
    input: String?,
    currentDirectoryPath: String?,
  ): Pair<Int, String> {
    if (command.isEmpty())
      throw ExException("E474: Invalid argument")

    var processBuilder = ProcessBuilder(command)
      .redirectErrorStream(true)

    if (currentDirectoryPath != null)
    {
      processBuilder = processBuilder.directory(File(currentDirectoryPath))
    }

    val proc = try { processBuilder.start() }
    catch (e: IOException)
    {
      logger.error(e.message ?: e.toString())
      throw ExException("E475: Invalid value for argument cmd: '${command[0]}' is not executable")
    }

    if (input != null) {
      proc.outputWriter().use { it.write(input) }
    }

    val exitValue = proc.waitFor()
    val output = proc.inputReader().use { it.readText() }
    return Pair(exitValue, output.replace("\u001B\\[[;\\d]*m".toRegex(), ""))
  }

  @Suppress("SameParameterValue")
  private fun doEscape(original: String, charsToEscape: String, escapeChar: String): String {
    var result = original
    for (c in charsToEscape.toCharArray()) {
      result = result.replace("$c", escapeChar + c)
    }
    return result
  }
}

