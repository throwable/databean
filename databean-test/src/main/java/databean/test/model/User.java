package databean.test.model;

import databean.DataClass;
import databean.Initial;

import javax.annotation.Nonnull;
import static databean.test.model.$User.*;

@DataClass
public interface User extends $User {
    /** constructor required field */
    @Initial
    int age();

    /** constructor required field */
    @Initial @Nonnull
    String name();

    Contact contact();

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
    interface BirthInfo extends $User.MBirthInfo {
        @DataClass
        interface Date extends $User.MBirthInfo.MDate {
            @Initial
            int year();
            @Initial
            int month();
            @Initial
            int day();
        }

        @Initial
        Date date();

        String place();
    }

    BirthInfo birthInfo();
}
