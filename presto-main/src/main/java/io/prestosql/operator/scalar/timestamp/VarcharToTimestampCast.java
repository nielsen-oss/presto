/*
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
package io.prestosql.operator.scalar.timestamp;

import com.google.common.annotations.VisibleForTesting;
import io.airlift.slice.Slice;
import io.prestosql.spi.PrestoException;
import io.prestosql.spi.function.LiteralParameter;
import io.prestosql.spi.function.LiteralParameters;
import io.prestosql.spi.function.ScalarOperator;
import io.prestosql.spi.function.SqlType;
import io.prestosql.spi.type.LongTimestamp;
import io.prestosql.type.DateTimes;

import java.time.DateTimeException;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;

import static com.google.common.base.Preconditions.checkArgument;
import static io.prestosql.operator.scalar.StringFunctions.trim;
import static io.prestosql.spi.StandardErrorCode.INVALID_CAST_ARGUMENT;
import static io.prestosql.spi.function.OperatorType.CAST;
import static io.prestosql.spi.type.TimestampType.MAX_PRECISION;
import static io.prestosql.spi.type.TimestampType.MAX_SHORT_PRECISION;
import static io.prestosql.type.DateTimes.MICROSECONDS_PER_SECOND;
import static io.prestosql.type.DateTimes.longTimestamp;
import static io.prestosql.type.DateTimes.rescale;
import static io.prestosql.type.DateTimes.round;
import static java.time.ZoneOffset.UTC;

@ScalarOperator(CAST)
public final class VarcharToTimestampCast
{
    private VarcharToTimestampCast() {}

    @LiteralParameters({"x", "p"})
    @SqlType("timestamp(p)")
    public static long castToShort(@LiteralParameter("p") long precision, @SqlType("varchar(x)") Slice value)
    {
        try {
            return castToShortTimestamp((int) precision, trim(value).toStringUtf8());
        }
        catch (IllegalArgumentException e) {
            throw new PrestoException(INVALID_CAST_ARGUMENT, "Value cannot be cast to timestamp: " + value.toStringUtf8(), e);
        }
        catch (DateTimeException e) {
            //Leverage highly specific error message from the source exception.
            throw new PrestoException(INVALID_CAST_ARGUMENT,
                    String.format("Value cannot be cast to timestamp; %s. ", e.getMessage()), e);
        }
    }

    @LiteralParameters({"x", "p"})
    @SqlType("timestamp(p)")
    public static LongTimestamp castToLong(@LiteralParameter("p") long precision, @SqlType("varchar(x)") Slice value)
    {
        try {
            return castToLongTimestamp((int) precision, trim(value).toStringUtf8());
        }
        catch (IllegalArgumentException e) {
            throw new PrestoException(INVALID_CAST_ARGUMENT, "Value cannot be cast to timestamp: " + value.toStringUtf8(), e);
        }
        catch (DateTimeException e) {
            //Leverage highly specific error message from the source exception.
            throw new PrestoException(INVALID_CAST_ARGUMENT,
                    String.format("Value cannot be cast to timestamp; %s. ", e.getMessage()), e);
        }
    }

    @VisibleForTesting
    public static long castToShortTimestamp(int precision, String value)
    {
        checkArgument(precision <= MAX_SHORT_PRECISION, "precision must be less than max short timestamp precision");

        Matcher matcher = DateTimes.DATETIME_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid timestamp: " + value);
        }

        String year = matcher.group("year");
        String month = matcher.group("month");
        String day = matcher.group("day");
        String hour = matcher.group("hour");
        String minute = matcher.group("minute");
        String second = matcher.group("second");
        String fraction = matcher.group("fraction");

        long epochSecond = ZonedDateTime.of(
                Integer.parseInt(year),
                Integer.parseInt(month),
                Integer.parseInt(day),
                hour == null ? 0 : Integer.parseInt(hour),
                minute == null ? 0 : Integer.parseInt(minute),
                second == null ? 0 : Integer.parseInt(second),
                0,
                UTC)
                .toEpochSecond();

        int actualPrecision = 0;
        long fractionValue = 0;
        if (fraction != null) {
            actualPrecision = fraction.length();
            fractionValue = Long.parseLong(fraction);
        }

        if (actualPrecision > precision) {
            fractionValue = round(fractionValue, actualPrecision - precision);
        }

        // scale to micros
        return epochSecond * MICROSECONDS_PER_SECOND + rescale(fractionValue, actualPrecision, 6);
    }

    @VisibleForTesting
    public static LongTimestamp castToLongTimestamp(int precision, String value)
    {
        checkArgument(precision > MAX_SHORT_PRECISION && precision <= MAX_PRECISION, "precision out of range");

        Matcher matcher = DateTimes.DATETIME_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid timestamp: " + value);
        }

        String year = matcher.group("year");
        String month = matcher.group("month");
        String day = matcher.group("day");
        String hour = matcher.group("hour");
        String minute = matcher.group("minute");
        String second = matcher.group("second");
        String fraction = matcher.group("fraction");

        long epochSecond = ZonedDateTime.of(
                Integer.parseInt(year),
                Integer.parseInt(month),
                Integer.parseInt(day),
                hour == null ? 0 : Integer.parseInt(hour),
                minute == null ? 0 : Integer.parseInt(minute),
                second == null ? 0 : Integer.parseInt(second),
                0,
                UTC)
                .toEpochSecond();

        int actualPrecision = 0;
        long fractionValue = 0;
        if (fraction != null) {
            actualPrecision = fraction.length();
            fractionValue = Long.parseLong(fraction);
        }

        if (actualPrecision > precision) {
            fractionValue = round(fractionValue, actualPrecision - precision);
        }

        return longTimestamp(epochSecond, rescale(fractionValue, actualPrecision, 12));
    }
}
