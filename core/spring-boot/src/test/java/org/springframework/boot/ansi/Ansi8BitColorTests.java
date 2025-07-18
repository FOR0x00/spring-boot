/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.ansi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link Ansi8BitColor}.
 *
 * @author Toshiaki Maki
 * @author Phillip Webb
 */
class Ansi8BitColorTests {

	@Test
	void toStringWhenForegroundAddsCorrectPrefix() {
		assertThat(Ansi8BitColor.foreground(208)).hasToString("38;5;208");
	}

	@Test
	void toStringWhenBackgroundAddsCorrectPrefix() {
		assertThat(Ansi8BitColor.background(208)).hasToString("48;5;208");
	}

	@Test
	void foregroundWhenOutsideBoundsThrowsException() {
		assertThatIllegalArgumentException().isThrownBy(() -> Ansi8BitColor.foreground(-1))
			.withMessage("'code' must be between 0 and 255");
		assertThatIllegalArgumentException().isThrownBy(() -> Ansi8BitColor.foreground(256))
			.withMessage("'code' must be between 0 and 255");
	}

	@Test
	void backgroundWhenOutsideBoundsThrowsException() {
		assertThatIllegalArgumentException().isThrownBy(() -> Ansi8BitColor.background(-1))
			.withMessage("'code' must be between 0 and 255");
		assertThatIllegalArgumentException().isThrownBy(() -> Ansi8BitColor.background(256))
			.withMessage("'code' must be between 0 and 255");
	}

	@Test
	void equalsAndHashCode() {
		Ansi8BitColor one = Ansi8BitColor.foreground(123);
		Ansi8BitColor two = Ansi8BitColor.foreground(123);
		Ansi8BitColor three = Ansi8BitColor.background(123);
		assertThat(one).hasSameHashCodeAs(two);
		assertThat(one).isEqualTo(one).isEqualTo(two).isNotEqualTo(three).isNotNull().isNotEqualTo("foo");
	}

}
