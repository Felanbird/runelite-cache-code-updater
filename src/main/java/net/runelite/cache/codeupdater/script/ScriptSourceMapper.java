/*
 * Copyright (c) 2018 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.cache.codeupdater.script;


import java.util.Objects;
import net.runelite.cache.codeupdater.mapper.Mapper;
import net.runelite.cache.script.Instruction;

public class ScriptSourceMapper implements Mapper<ScriptSource.Line>
{
	@Override
	public double difference(ScriptSource.Line a, ScriptSource.Line b)
	{
		Instruction aii = a.getInstruction();
		String ai = aii == null ? a.getOpcode() : aii.getName();
		Instruction bii = b.getInstruction();
		String bi = bii == null ? b.getOpcode() : bii.getName();

		double sim = 0;

		if (Objects.equals(ai, bi))
		{
			sim += .7;
			if (Objects.equals(a.getOperand(), b.getOperand()))
			{
				sim += .3;
			}
		}

		return 1 - sim;
	}

	@Override
	public String string(ScriptSource.Line v)
	{
		return v.getOpcode() + v.getOperand();
	}
}
