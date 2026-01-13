package pik.domain.ingenico;

import epis5.duk.bck.core.crypto.Uk3DesCbcCipher;
import epis5.duk.bck.core.sam.SamDuk;
import epis5.duk.bck.core.sam.SamSlotStatus;
import epis5.duk.bck.core.sam.SamType;
import epis5.duk.bck.core.sam.UnlockStatus;
import epis5.duk.bck.core.sam.apdu.ApduRequest;
import epis5.duk.bck.core.sam.apdu.ApduRequestFactory;
import epis5.duk.bck.core.sam.apdu.ApduResponse;
import epis5.duk.bck.core.sam.apdu.StatusWord;
import epis5.duk.bck.core.sam.auth.Auth;
import epis5.duk.bck.core.sam.auth.AuthProcessState;
import epis5.duk.bck.core.sam.auth.AuthResult;
import epis5.ingenico.transit.prot.*;
import jCommons.ByteArrayFormatter;
import jCommons.comm.protocol.IPeriodicalChecker;
import jCommons.logging.ILogger;
import jCommons.logging.LoggerFactory;
import jCommons.timer.TickCounter;
import jCommons.utils.ByteUtils;
import pik.common.ELogger;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.inject.Inject;

public class SamDukAuthStateMachine implements IPeriodicalChecker {
    private static final int RESPONSE_TIMEOUT_MILLS = 1500;
    private static final int CHECK_PERIOD_MILLS = RESPONSE_TIMEOUT_MILLS + 50;
    private static final boolean INSECURE_DEBUG_LOGGING_ENABLED = false;

    private final IngenicoReaderDevice reader;
    private final ITransitProtMsgOutputter msgOutputter;
    private final ILogger transitLogger;
    private final TickCounter periodTC;
    private boolean getChallengeCmdSent, getChallengeCmdReceived;
    private boolean extAuthCmdSent, extAuthCmdReceived;

    @Inject
    public SamDukAuthStateMachine(IngenicoReaderDevice reader, ITransitProtMsgOutputter msgOutputter) {
        this.reader = reader;
        this.msgOutputter = msgOutputter;

        this.transitLogger = LoggerFactory.get(ELogger.INGENICO_TRANSIT);
        this.periodTC = TickCounter.instanceFromNow();

        this.getChallengeCmdSent = false;
        this.getChallengeCmdReceived = false;
        this.extAuthCmdSent = false;
        this.extAuthCmdReceived = false;
    }

    @Override
    public void periodicalCheck() {
        if (isAuthProcessFinished()) {
            return;
        }

        if (!isConditionForAuthProcess()) {
            return;
        }

        if (!periodTC.isElapsedMills(CHECK_PERIOD_MILLS)) {
            return;
        }
        periodTC.recordNow();

        onCheckPeriodElapsed();
    }

    private SamDuk getSamDuk() {
        return reader.getSamDuk();
    }

    private Auth getAuth() {
        return getSamDuk().getAuth();
    }

    private boolean isAuthProcessFinished() {
        return getAuth().isProcessStateFinished();
    }

    private boolean isConditionForAuthProcess() {
        return reader.isInitStatusDone() && reader.getTransitApp().isConnectedAndAppAlive();
    }

    private void makeCheckPeriodElapsed() {
        periodTC.fromNowBackMills(CHECK_PERIOD_MILLS);
    }

    private void onCheckPeriodElapsed() {
        AuthProcessState processState = getAuth().getProcessState();
        switch (processState) {
            case INIT_VERIFYING_SAM_DETECTED:
                onVerifyingSamDetected();
                break;
            case STARTING:
                onStarting();
                break;
            case SELECTING_INFO_APPLET:
                checkSelectInfoApplet();
                break;
            case VERIFY_SAM_TYPE:
                checkVerifySamType();
                break;
            case VERIFY_SAM_NETWORK_ID:
                checkVerifySamNetworkId();
                break;
            case READING_SAM_NUMBER:
                checkReadingSamNumber();
                break;

            case SELECTING_CTRL_APPLET:
                checkSelectCtrlApplet();
                break;
            case CHECKING_SAM_AUTH_STATE_BEFORE_AUTH:
                checkSamAuthStateBeforeAuth();
                break;
            case GET_CHALLENGE:
                checkGetChallenge();
                break;
            case EXT_AUTHENTICATE:
                checkExtAuthenticate();
                break;
            case CHECKING_SAM_AUTH_STATE_AFTER_AUTH:
                checkSamAuthStateAfterAuth();
                break;

            case FINISHED:
                break;
            default:
                setProcessFinishedOnError("Unknown AuthProcessState: " + processState);
                break;
        }
    }

    //**************************************************************
    //***************** VERIFYING_SAM_DETECTED *********************
    //**************************************************************

    private void onVerifyingSamDetected() {
        SamDuk samDuk = getSamDuk();
        if (samDuk.getSlotStatus() == SamSlotStatus.POWER_ON && samDuk.getSamAtr().isDukAtr()) {
            changeState(AuthProcessState.STARTING);
        } else {
            setProcessFinishedOnError("SAM DUK not detected in the slot!");
        }
    }

    //**************************************************************
    //***************** STARTING ***********************************
    //**************************************************************

    private void onStarting() {
        localAuthReset();

        changeState(AuthProcessState.SELECTING_INFO_APPLET);
    }

    private void localAuthReset() {
        this.getChallengeCmdSent = false;
        this.getChallengeCmdReceived = false;
        this.extAuthCmdSent = false;
        this.extAuthCmdReceived = false;
    }

    //**************************************************************
    //********** SELECTING_INFO_APPLET *****************************
    //**************************************************************

    private void checkSelectInfoApplet() {
        log("Tx SAM_TRANSMIT(SELECT INFO APPLET)");

        Payload payload = new PayloadBuilder()
                .command(Command.SAM_TRANSMIT)
                .samSlot(getSamDuk().getSlotIndex())
                .apduRequest(ApduRequestFactory.selectInfoApplet().getCommandBytes())
                .build();

        sendRequest(payload, createSelectInfoAppletoCallback());
    }

    private ITransitProtServiceProcessor createSelectInfoAppletoCallback() {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, "SAM_TRANSMIT(SELECT INFO APPLET)-Auth", transitLogger);
                return;
            }

            if (getAuth().getProcessState() != AuthProcessState.SELECTING_INFO_APPLET) {
                logWarn("SAM_TRANSMIT(SELECT INFO APPLET)-Auth response when processState != AuthProcessState.SELECTING_INFO_APPLET");
                return;
            }

            log("Rx SAM_TRANSMIT(SELECT INFO APPLET)-Auth: " + incMsg.getPayload().formatForLog());

            if (ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                TlvRecord apduRespRec = incMsg.getPayload().getRecord(TagType.APDU_RESPONSE);
                if (apduRespRec != null) {
                    StatusWord statusWord = new StatusWord(apduRespRec.getValue());
                    if (statusWord.isSuccess()) {
                        changeState(AuthProcessState.VERIFY_SAM_TYPE);
                    } else {
                        setProcessFinishedOnError("Select info applet, APDU response status word is error: " + statusWord.getBytesAsHexString());
                    }
                } else {
                    setProcessFinishedOnError("Select INFO APPLET missing APDU_RESPONSE tag!");
                }

            } else {
                ResponseUtils.logResponseCode(incMsg.getPayload(), "SAM_TRANSMIT(SELECT INFO APPLET)-Auth", transitLogger);
            }
        };
    }

    //**************************************************************
    //********** VERIFY_SAM_TYPE ***********************************
    //**************************************************************

    private void checkVerifySamType() {
        log("Tx SAM_TRANSMIT(VERIFY SAM TYPE)");

        Payload payload = new PayloadBuilder()
                .command(Command.SAM_TRANSMIT)
                .samSlot(getSamDuk().getSlotIndex())
                .apduRequest(ApduRequestFactory.getSamType().getCommandBytes())
                .build();

        sendRequest(payload, createGetSamTypeCallback());
    }

    private ITransitProtServiceProcessor createGetSamTypeCallback() {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, "SAM_TRANSMIT(VERIFY SAM TYPE)", transitLogger);
                return;
            }

            if (getAuth().getProcessState() != AuthProcessState.VERIFY_SAM_TYPE) {
                logWarn("SAM_TRANSMIT(VERIFY SAM TYPE) response when processState != AuthProcessState.VERIFY_SAM_TYPE");
                return;
            }

            log("Rx SAM_TRANSMIT(VERIFY SAM TYPE): " + incMsg.getPayload().formatForLog());

            if (ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                TlvRecord apduRespRec = incMsg.getPayload().getRecord(TagType.APDU_RESPONSE);
                if (apduRespRec != null) {
                    ApduResponse apduResp = new ApduResponse(apduRespRec.getValue());
                    if (apduResp.isStatusWordSuccess() && apduResp.isSamAndDesfireStatusOk()) {
                        int samTypeCode = ByteUtils.toInt(apduResp.getData()[0]);
                        SamType samType = SamType.fromCode(samTypeCode);

                        // Store the found SAM type for status display
                        reader.setFoundSamType(samType);
                        log(String.format("Stored foundSamType: %s (code=0x%02X)", samType, samTypeCode));

                        log(String.format("SAM type_in_slot=%s, type_in_slot_code=%d, type_required=%s", samType, samTypeCode, getSamDuk().getSamType()));

                        if (samType == getSamDuk().getSamType()) {
                            changeState(AuthProcessState.VERIFY_SAM_NETWORK_ID);
                        } else {
                            setProcessFinishedOnError("SAM_TRANSMIT(VERIFY SAM TYPE) - Invalid SAM module inserted in slot, not type: " + getSamDuk().getSamType());
                        }
                    } else {
                        setProcessFinishedOnError("SAM_TRANSMIT(VERIFY SAM TYPE) - ApduResponse status word error!");
                    }
                } else {
                    setProcessFinishedOnError("SAM_TRANSMIT(VERIFY SAM TYPE) missing APDU_RESPONSE tag!");
                }

            } else {
                ResponseUtils.logResponseCode(incMsg.getPayload(), "SAM_TRANSMIT(VERIFY SAM TYPE)", transitLogger);
            }
        };
    }

    //**************************************************************
    //********** VERIFY_SAM_NETWORK_ID *****************************
    //**************************************************************

    private void checkVerifySamNetworkId() {
        log("Tx SAM_TRANSMIT(VERIFY SAM NETWORK ID)");

        Payload payload = new PayloadBuilder()
                .command(Command.SAM_TRANSMIT)
                .samSlot(getSamDuk().getSlotIndex())
                .apduRequest(ApduRequestFactory.getNetworkId().getCommandBytes())
                .build();

        sendRequest(payload, createGetSamNetworkIdCallback());
    }

    private ITransitProtServiceProcessor createGetSamNetworkIdCallback() {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, "SAM_TRANSMIT(VERIFY NETWORK ID)", transitLogger);
                return;
            }

            if (getAuth().getProcessState() != AuthProcessState.VERIFY_SAM_NETWORK_ID) {
                logWarn("SAM_TRANSMIT(VERIFY NETWORK ID) response when processState != AuthProcessState.VERIFY_SAM_NETWORK_ID");
                return;
            }

            log("Rx SAM_TRANSMIT(VERIFY NETWORK ID): " + incMsg.getPayload().formatForLog());

            if (ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                TlvRecord apduRespRec = incMsg.getPayload().getRecord(TagType.APDU_RESPONSE);
                if (apduRespRec != null) {
                    ApduResponse apduResp = new ApduResponse(apduRespRec.getValue());
                    if (apduResp.isStatusWordSuccess() && apduResp.isSamAndDesfireStatusOk()) {
                        int networkId = ByteUtils.intFromLittleEndArr(apduResp.getData());

                        log(String.format("SAM network_id_in_slot=%d, network_id_required=%d", networkId, SamDuk.NETWORK_ID));

                        if (networkId == SamDuk.NETWORK_ID) {
                            changeState(AuthProcessState.READING_SAM_NUMBER);
                        } else {
                            setProcessFinishedOnError("SAM_TRANSMIT(VERIFY NETWORK ID) - Invalid SAM module inserted in slot, expected network ID: " + SamDuk.NETWORK_ID);
                        }
                    } else {
                        setProcessFinishedOnError("SAM_TRANSMIT(VERIFY NETWORK ID) - ApduResponse status word error!");
                    }
                } else {
                    setProcessFinishedOnError("SAM_TRANSMIT(VERIFY NETWORK ID) missing APDU_RESPONSE tag!");
                }

            } else {
                ResponseUtils.logResponseCode(incMsg.getPayload(), "SAM_TRANSMIT(VERIFY NETWORK ID)", transitLogger);
            }
        };
    }

    //**************************************************************
    //********** READING_SAM_NUMBER ********************************
    //**************************************************************

    private void checkReadingSamNumber() {
        log("Tx SAM_TRANSMIT(READ SAM NUMBER)");

        Payload payload = new PayloadBuilder()
                .command(Command.SAM_TRANSMIT)
                .samSlot(getSamDuk().getSlotIndex())
                .apduRequest(ApduRequestFactory.getSamNumber().getCommandBytes())
                .build();

        sendRequest(payload, createGetSamNumberCallback());
    }

    private ITransitProtServiceProcessor createGetSamNumberCallback() {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, "SAM_TRANSMIT(READ SAM NUMBER)", transitLogger);
                return;
            }

            if (getAuth().getProcessState() != AuthProcessState.READING_SAM_NUMBER) {
                logWarn("SAM_TRANSMIT(READ SAM NUMBER) response when processState != AuthProcessState.READING_SAM_NUMBER");
                return;
            }

            log("Rx SAM_TRANSMIT(READ SAM NUMBER): " + incMsg.getPayload().formatForLog());

            if (ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                TlvRecord apduRespRec = incMsg.getPayload().getRecord(TagType.APDU_RESPONSE);
                if (apduRespRec != null) {
                    ApduResponse apduResp = new ApduResponse(apduRespRec.getValue());
                    if (apduResp.isStatusWordSuccess() && apduResp.isSamAndDesfireStatusOk()) {
                        byte[] samNumberData = apduResp.getData();
                        String samNumberHex = ByteArrayFormatter.toHexNoSpaces(samNumberData);

                        log("SAM number received: " + samNumberHex);

                        getSamDuk().setSamNumber(samNumberHex);

                        changeState(AuthProcessState.SELECTING_CTRL_APPLET);
                    } else {
                        setProcessFinishedOnError("SAM_TRANSMIT(READ SAM NUMBER) - ApduResponse status word error!");
                    }
                } else {
                    setProcessFinishedOnError("SAM_TRANSMIT(READ SAM NUMBER) missing APDU_RESPONSE tag!");
                }

            } else {
                ResponseUtils.logResponseCode(incMsg.getPayload(), "SAM_TRANSMIT(READ SAM NUMBER)", transitLogger);
            }
        };
    }

    //**************************************************************
    //********** SELECTING_CTRL_APPLET *****************************
    //**************************************************************

    private void checkSelectCtrlApplet() {
        log("Tx SAM_TRANSMIT(SELECT CTRL APPLET)");

        Payload payload = new PayloadBuilder()
                .command(Command.SAM_TRANSMIT)
                .samSlot(getSamDuk().getSlotIndex())
                .apduRequest(ApduRequestFactory.selectCtrlApplet().getCommandBytes())
                .build();

        sendRequest(payload, createSelectCtrlAppletoCallback());
    }

    private ITransitProtServiceProcessor createSelectCtrlAppletoCallback() {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, "SAM_TRANSMIT(SELECT CTRL APPLET)-Auth", transitLogger);
                return;
            }

            if (getAuth().getProcessState() != AuthProcessState.SELECTING_CTRL_APPLET) {
                logWarn("SAM_TRANSMIT(SELECT CTRL APPLET)-Auth response when processState != AuthProcessState.SELECTING_CTRL_APPLET");
                return;
            }

            log("Rx SAM_TRANSMIT(SELECT CTRL APPLET)-Auth: " + incMsg.getPayload().formatForLog());

            if (ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                TlvRecord apduRespRec = incMsg.getPayload().getRecord(TagType.APDU_RESPONSE);
                if (apduRespRec != null) {
                    StatusWord statusWord = new StatusWord(apduRespRec.getValue());
                    if (statusWord.isSuccess()) {
                        changeState(AuthProcessState.CHECKING_SAM_AUTH_STATE_BEFORE_AUTH);
                    } else {
                        setProcessFinishedOnError("Select ctrl applet, APDU response status word is error: " + statusWord.getBytesAsHexString());
                    }
                } else {
                    setProcessFinishedOnError("Select CTRL APPLET missing APDU_RESPONSE tag!");
                }

            } else {
                ResponseUtils.logResponseCode(incMsg.getPayload(), "SAM_TRANSMIT(SELECT CTRL APPLET)-Auth", transitLogger);
            }
        };
    }

    //**************************************************************
    //********** CHECKING_SAM_AUTH_STATE_BEFORE_AUTH ***************
    //**************************************************************

    private void checkSamAuthStateBeforeAuth() {
        //Vzdy po restartu nebo po rozpadu a navazani spojeni proved znovu autentizaci a ustanov RK
        //Pripad kdy se treba jen prerusi sitove spojeni mezi cteckou a EVK, tzn nikdo neprosel restartem a RK je jiz ustanoven nebudeme osetrovat
        changeState(AuthProcessState.GET_CHALLENGE);
    }

    //**************************************************************
    //********** GET_CHALLENGE *************************************
    //**************************************************************

    private void checkGetChallenge() {
        if (getChallengeCmdSent) {
            if (getAuth().isElapsedFromLastProcessStateChangedMills(RESPONSE_TIMEOUT_MILLS * 4)) {
                setProcessFinishedOnError("SAM_TRANSMIT(GetChallenge) response timeout!");
            }
            return;
        }

        log("Tx SAM_TRANSMIT(GetChallenge)");

        Payload payload = new PayloadBuilder()
                .command(Command.SAM_TRANSMIT)
                .samSlot(getSamDuk().getSlotIndex())
                .apduRequest(ApduRequestFactory.authGetChallenge().getCommandBytes())
                .build();

        getChallengeCmdSent = true;

        sendRequest(payload, createGetChallengeCallback());
    }

    private ITransitProtServiceProcessor createGetChallengeCallback() {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, "SAM_TRANSMIT(GetChallenge)-Auth", transitLogger);
                return;
            }

            if (getChallengeCmdReceived) {
                logWarn("SAM_TRANSMIT(GetChallenge) response when getChallengeCmdReceived == true");
                return;
            }
            getChallengeCmdReceived = true;

            log("Rx SAM_TRANSMIT(GetChallenge): " + incMsg.getPayload().formatForLog());

            if (ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                TlvRecord apduRespRec = incMsg.getPayload().getRecord(TagType.APDU_RESPONSE);
                if (apduRespRec != null) {
                    ApduResponse apduResp = new ApduResponse(apduRespRec.getValue());
                    if (apduResp.isStatusWordSuccess() && apduResp.isSamAndDesfireStatusOk()) {
                        byte[] encryptedRndSam = apduResp.getData();
                        try {
                            Auth auth = getAuth();
                            byte[] rndSam = auth.getCipher().decrypt(encryptedRndSam);
                            auth.setRndSam(rndSam);

                            if (INSECURE_DEBUG_LOGGING_ENABLED) {
                                log("encryptedRndSam=" + ByteArrayFormatter.toHex(encryptedRndSam));
                                log("rndSam=" + ByteArrayFormatter.toHex(rndSam));
                                log("rndTerm=" + ByteArrayFormatter.toHex(auth.getRndTerm()));
                            }

                            changeState(AuthProcessState.EXT_AUTHENTICATE);

                        } catch (IllegalBlockSizeException | BadPaddingException e) {
                            transitLogger.error("GetChallenge - decryption error", e);
                            setProcessFinishedOnError("GetChallenge - decryption error!");
                        }
                    } else {
                        setProcessFinishedOnError("GetChallenge - ApduResponse status word error!");
                    }
                } else {
                    setProcessFinishedOnError("GetChallenge missing APDU_RESPONSE tag!");
                }
            } else {
                ResponseUtils.logResponseCode(incMsg.getPayload(), "SAM_TRANSMIT(GetChallenge)-Auth", transitLogger);
                setProcessFinishedOnError("SAM_TRANSMIT(GetChallenge) response code is error");
            }
        };
    }

    //**************************************************************
    //********** EXT_AUTHENTICATE **********************************
    //**************************************************************

    private void checkExtAuthenticate() {
        if (extAuthCmdSent) {
            if (getAuth().isElapsedFromLastProcessStateChangedMills(RESPONSE_TIMEOUT_MILLS * 4)) {
                setProcessFinishedOnError("SAM_TRANSMIT(ExtAuthenticate) response timeout!");
            }
            return;
        }

        log("Tx SAM_TRANSMIT(ExtAuthenticate)");

        byte[] exAuthData = getAuth().formExtAuthenticateData();
        byte[] exAuthDataEncrypted;

        try {
            exAuthDataEncrypted = getAuth().getCipher().encrypt(exAuthData);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            transitLogger.error("ExtAuthenticate - encryption error", e);
            setProcessFinishedOnError("ExtAuthenticate - encryption error!");
            return;
        }

        ApduRequest apduRequest = ApduRequestFactory.authExtAuthenticate(exAuthDataEncrypted);

        if (INSECURE_DEBUG_LOGGING_ENABLED) {
            log("extAuth=" + ByteArrayFormatter.toHex(exAuthData));
            log("exAuthDataEncrypted=" + ByteArrayFormatter.toHex(exAuthData));
            log("apduRequest=" + ByteArrayFormatter.toHex(apduRequest.getCommandBytes()));
        }

        Payload payload = new PayloadBuilder()
                .command(Command.SAM_TRANSMIT)
                .samSlot(getSamDuk().getSlotIndex())
                .apduRequest(apduRequest.getCommandBytes())
                .build();

        extAuthCmdSent = true;

        sendRequest(payload, createExtAuthenticateCallback());
    }

    private ITransitProtServiceProcessor createExtAuthenticateCallback() {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, "SAM_TRANSMIT(ExtAuthenticate)-Auth", transitLogger);
                return;
            }

            if (extAuthCmdReceived) {
                logWarn("SAM_TRANSMIT(ExtAuthenticate) response when extAuthCmdReceived == true");
                return;
            }
            extAuthCmdReceived = true;

            log("Rx SAM_TRANSMIT(ExtAuthenticate): " + incMsg.getPayload().formatForLog());

            if (ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                TlvRecord apduRespRec = incMsg.getPayload().getRecord(TagType.APDU_RESPONSE);
                if (apduRespRec != null) {
                    ApduResponse apduResp = new ApduResponse(apduRespRec.getValue());
                    if (apduResp.isStatusWordSuccess() && apduResp.isSamAndDesfireStatusOk()) {
                        byte[] encryptedRndTermRotated = apduResp.getData();
                        try {
                            Auth auth = getAuth();
                            byte[] rndTermRotated = auth.getCipher().decrypt(encryptedRndTermRotated);

                            if (INSECURE_DEBUG_LOGGING_ENABLED) {
                                log("encryptedRndTermRotated=" + ByteArrayFormatter.toHex(encryptedRndTermRotated));
                                log("rndTermRotated=" + ByteArrayFormatter.toHex(rndTermRotated));
                                log("verifyRndTerm=" + auth.verifyRndTerm(rndTermRotated));
                            }

                            if (auth.verifyRndTerm(rndTermRotated)) {
                                changeState(AuthProcessState.CHECKING_SAM_AUTH_STATE_AFTER_AUTH);
                            } else {
                                setProcessFinishedOnError("ExtAuthenticate - rndTerm verification failed!");
                            }
                        } catch (IllegalBlockSizeException | BadPaddingException e) {
                            transitLogger.error("ExtAuthenticate - decryption error", e);
                            setProcessFinishedOnError("ExtAuthenticate - decryption error!");
                        }
                    } else {
                        setProcessFinishedOnError("ExtAuthenticate - ApduResponse status word error!");
                    }
                } else {
                    setProcessFinishedOnError("ExtAuthenticate missing APDU_RESPONSE tag!");
                }
            } else {
                ResponseUtils.logResponseCode(incMsg.getPayload(), "SAM_TRANSMIT(ExtAuthenticate)-Auth", transitLogger);
                setProcessFinishedOnError("SAM_TRANSMIT(ExtAuthenticate) response code is error");
            }
        };
    }

    //**************************************************************
    //********** CHECKING_SAM_AUTH_STATE_AFTER_AUTH ***************
    //**************************************************************

    private void checkSamAuthStateAfterAuth() {
        log("Tx SAM_TRANSMIT(isUnlocked? - after auth)");

        Payload payload = new PayloadBuilder()
                .command(Command.SAM_TRANSMIT)
                .samSlot(getSamDuk().getSlotIndex())
                .apduRequest(ApduRequestFactory.isUnlocked().getCommandBytes())
                .build();

        sendRequest(payload, createIsUnlockedAfterAuthCallback());
    }

    private ITransitProtServiceProcessor createIsUnlockedAfterAuthCallback() {
        return (writtenMsg, incMsg) -> {
            if (!ResponseUtils.isValidResponse(incMsg)) {
                ResponseUtils.logInvalidResponse(writtenMsg, incMsg, "SAM_TRANSMIT(isUnlocked? - after auth)", transitLogger);
                return;
            }

            if (getAuth().getProcessState() != AuthProcessState.CHECKING_SAM_AUTH_STATE_AFTER_AUTH) {
                logWarn("SAM_TRANSMIT(isUnlocked? - after auth) response when processState != AuthProcessState.CHECKING_SAM_AUTH_STATE_AFTER_AUTH");
                return;
            }

            log("Rx SAM_TRANSMIT(isUnlocked? - after auth): " + incMsg.getPayload().formatForLog());

            if (ResponseUtils.isResponseCodeOk(incMsg.getPayload())) {
                TlvRecord apduRespRec = incMsg.getPayload().getRecord(TagType.APDU_RESPONSE);
                if (apduRespRec != null) {
                    ApduResponse apduResp = new ApduResponse(apduRespRec.getValue());
                    if (apduResp.isStatusWordSuccess() && apduResp.isSamAndDesfireStatusOk()) {
                        int unlockStatusCode = ByteUtils.toInt(apduResp.getData()[0]);
                        UnlockStatus unlockStatus = UnlockStatus.fromCode(unlockStatusCode);
                        reader.getSamDuk().setUnlockStatus(unlockStatus);
                        log("SAM UnlockStatus=" + unlockStatus);

                        if (unlockStatus != UnlockStatus.LOCKED_INVALID_SESSION_KEY_ERROR) {
                            setFinishedOk();
                        } else {
                            setProcessFinishedOnError("SAM not authenticated after auth finished, unlockStatus=" + unlockStatus);
                        }
                    } else {
                        setProcessFinishedOnError("SAM_TRANSMIT(isUnlocked? - after auth) - ApduResponse status word error!");
                    }
                } else {
                    setProcessFinishedOnError("SAM_TRANSMIT(isUnlocked? - after auth) missing APDU_RESPONSE tag!");
                }

            } else {
                ResponseUtils.logResponseCode(incMsg.getPayload(), "SAM_TRANSMIT(isUnlocked? - after auth)", transitLogger);
            }
        };
    }

    //**************************************************************
    //**************************************************************
    //**************************************************************

    private void sendRequest(Payload payload, ITransitProtServiceProcessor callback) {
        TransitProtMsg transitProtMsg = TransitProtMsg.newRequest(payload, reader.getTransitApp().getSocketAddress(), RESPONSE_TIMEOUT_MILLS, callback);
        msgOutputter.outputMsg(transitProtMsg);
    }

    private void setFinishedOk() {
        byte[] sessionKey16Bytes = getAuth().formSessionKey16Bytes();

        getSamDuk().setSessionCipher(new Uk3DesCbcCipher(sessionKey16Bytes));

        if (INSECURE_DEBUG_LOGGING_ENABLED) {
            log("sessionKey=" + ByteArrayFormatter.toHex(sessionKey16Bytes));
        }

        getAuth().setProcessFinished(AuthResult.success(sessionKey16Bytes));
    }

    private void setProcessFinishedOnError(String message) {
        logError("AuthProcess finished with error, " + message);
        getAuth().setProcessFinished(AuthResult.fail(message));
    }

    private void changeState(AuthProcessState newState) {
        getAuth().setProcessState(newState);
        makeCheckPeriodElapsed();
    }

    private void log(String msg) {
        transitLogger.info("SAM auth> " + msg);
    }

    private void logWarn(String msg) {
        transitLogger.warn("SAM auth> " + msg);
    }

    private void logError(String msg) {
        transitLogger.error("SAM auth> " + msg);
    }
}
