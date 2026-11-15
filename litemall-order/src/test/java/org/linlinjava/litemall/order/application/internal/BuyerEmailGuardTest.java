package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallUserMapper;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.order.testsupport.CapturedLogs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/** Submit-time email guarantee: who is refused, who is not, and what is logged. */
class BuyerEmailGuardTest {

    private final LitemallUserMapper userMapper = mock(LitemallUserMapper.class);

    private void stubUser(int id, String email) {
        LitemallUser user = new LitemallUser();
        user.setId(id);
        user.setEmail(email);
        when(userMapper.selectByPrimaryKey(id)).thenReturn(user);
    }

    @Test
    void accountWithEmail_isAllowed() {
        stubUser(7, "buyer@example.com");
        assertThat(new BuyerEmailGuard(userMapper, true).refusalFor(7)).isEmpty();
    }

    @Test
    void accountWithNullEmail_isRefusedAndLogged() {
        stubUser(7, null);
        try (CapturedLogs logs = new CapturedLogs(BuyerEmailGuard.class)) {
            assertThat(new BuyerEmailGuard(userMapper, true).refusalFor(7)).contains(BuyerEmailGuard.REFUSAL);
            assertThat(logs.warnings()).anyMatch(m -> m.contains("user 7") && m.contains("no email address"));
        }
    }

    @Test
    void accountWithBlankEmail_isRefused() {
        stubUser(7, "   ");
        assertThat(new BuyerEmailGuard(userMapper, true).refusalFor(7)).contains(BuyerEmailGuard.REFUSAL);
    }

    @Test
    void killSwitchOff_allowsAnEmailLessAccountWithoutLookingItUp() {
        stubUser(7, null);
        assertThat(new BuyerEmailGuard(userMapper, false).refusalFor(7)).isEmpty();
        verify(userMapper, never()).selectByPrimaryKey(any());
    }

    @Test
    void failedLookup_neverBlocksTheSale_andIsLogged() {
        when(userMapper.selectByPrimaryKey(7)).thenThrow(new RuntimeException("db down"));
        try (CapturedLogs logs = new CapturedLogs(BuyerEmailGuard.class)) {
            assertThat(new BuyerEmailGuard(userMapper, true).refusalFor(7)).isEmpty();
            assertThat(logs.warnings()).anyMatch(m -> m.contains("lookup failed"));
        }
    }

    @Test
    void unknownUserOrMissingId_isNotThisGuardsQuestion() {
        BuyerEmailGuard guard = new BuyerEmailGuard(userMapper, true);
        assertThat(guard.refusalFor(99)).isEmpty();
        assertThat(guard.refusalFor(null)).isEmpty();
    }
}
