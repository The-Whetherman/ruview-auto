package com.whetherman.ruviewauto;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

public class RuViewCarAppService extends CarAppService {
    @NonNull
    @Override
    public HostValidator createHostValidator() {
        // POC only: allow any host. Tighten before any real release.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull
    @Override
    public Session onCreateSession() {
        return new RuViewSession();
    }
}
