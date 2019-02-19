package databean.test.model;

import databean.DataClass;
import databean.Initial;

import javax.annotation.Nonnull;

@DataClass
public interface IUser {
    /** constructor required field */
    @Initial
    int age();

    /** constructor required field */
    @Initial @Nonnull
    String name();

    IContact contact();

    /** default value must be set on a constructor call */
    default String hobby() {
        return "unknown";
    }

    /** default primitive value must be set on a constructor call */
    default boolean active() {
        return true;
    }

    /**
     * Non-null properties must be automatically initialized with theirs' type default value (empty string in this case)
     */
    @Nonnull
    String comments();

    @DataClass
    interface IBirthInfo {
        @DataClass
        interface IDate {
            @Initial
            int year();
            @Initial
            int month();
            @Initial
            int day();
        }

        @Initial
        IDate date();

        String place();
    }

    IBirthInfo birthInfo();
}
