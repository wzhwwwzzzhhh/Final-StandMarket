package com.fashion.utils;

import com.fashion.context.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RedisSimpleLock implements ILock{


    @Override
    public boolean tryLock(long timeoutSec) {
        return true;
    }

    @Override
    public void unlock() {

    }
}
