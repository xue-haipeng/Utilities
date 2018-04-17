  function compareDateTime(startTime, endTime) {
        var yearEqual = endTime.getUTCFullYear() === startTime.getUTCFullYear();
        var monthEqual = endTime.getUTCMonth() === startTime.getUTCMonth();
        var dateEqual = endTime.getUTCDate() === startTime.getUTCDate();
        var hourEqual = endTime.getUTCHours() === startTime.getUTCHours();
        var minuteEqual = endTime.getUTCMinutes() == startTime.getUTCMinutes();
        if (endTime.getUTCFullYear() < startTime.getUTCFullYear()
                || yearEqual && endTime.getUTCMonth() < startTime.getUTCMonth()
                || yearEqual && monthEqual && endTime.getUTCDate() < startTime.getUTCDate()
                || yearEqual && monthEqual && dateEqual && endTime.getUTCHours() < startTime.getUTCHours()
                || yearEqual && monthEqual && dateEqual && hourEqual && endTime.getUTCMinutes() < startTime.getUTCMinutes()) {
            return -1;
        } else if (yearEqual && monthEqual && dateEqual && hourEqual && minuteEqual) {
            return 0;
        } else {
            return 1;
        }
    }
