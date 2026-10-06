package com.aub.mobilebanking.uk;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

import android.content.Intent;
import android.util.Base64;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tapandpay.TapAndPay;
import com.google.android.gms.tapandpay.TapAndPayClient;
import com.google.android.gms.tapandpay.issuer.GeneratePaymentCredentialsRequest;
import com.google.android.gms.tapandpay.issuer.GeneratePaymentCredentialsResponse;
import com.google.android.gms.tapandpay.issuer.IsTokenizedRequest;
import com.google.android.gms.tapandpay.issuer.PaymentCredentialsGenerator;
import com.google.android.gms.tapandpay.issuer.PushTokenizeRequest;
import com.google.android.gms.tapandpay.issuer.TokenInfo;
import com.google.android.gms.tapandpay.issuer.UserAddress;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * This class echoes a string called from JavaScript.
 */
public class GoogleWalletPlugin extends CordovaPlugin {
    private static final String TAG = "GooglePayIssuer";
    private static final int REQUEST_CODE_PUSH_TOKENIZE = 3;
    private static final int REQUEST_CREATE_WALLET = 4;
    private CordovaInterface cordova;
    private TapAndPayClient tapAndPayClient;
    private String walletId;
    private String hardwareId;
    private CallbackContext callbackContext;
    private CallbackContext pushTokenizeCallbackContext;

    private JSONObject joReturn = new JSONObject();

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.cordova = cordova;
        tapAndPayClient = TapAndPay.getClient(this.cordova.getActivity());
        Log.i(TAG, "INITIALIZED");
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Log.i(TAG, action);
        Log.i(TAG, args.toString());

        this.callbackContext = callbackContext;

        if ("getActiveWalletID".equals(action)) {
            this.cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    getActiveWalletID(callbackContext);
                }
            });
            return true;
        } else if ("getStableHardwareId".equals(action)) {
            this.cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    getStableHardwareId(callbackContext);
                }
            });
            return true;
        } else if ("listTokens".equals(action)) {
            this.cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    listTokens(callbackContext);
                }
            });
            return true;
        } else if ("pushTokenize".equals(action)) {

            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
//                        String backendUrl = args.getString(0);
//                        String token = args.getString(0);
//                        String cardId = args.getString(0);
                        String displayName = args.getString(0);
                        String lastDigits = args.getString(1);
//                        JSONObject address = args.getJSONObject(3);
                        // Parse the incoming JSON string explicitly into a Java JSONObject
                        String addressString = args.getString(2);
                        JSONObject addressJson = new JSONObject(addressString);


                        String network = args.getString(3);

//                        pushTokenize(backendUrl, token, cardId,displayName,lastDigits, address,network, callbackContext);
                        pushTokenize(displayName,lastDigits, addressJson,network, callbackContext);
                    } catch (Exception e) {
                        callbackContext.error(e.getMessage());
                    }
                }
            });

            return true;
        } else if ("isCardInWallet".equals(action)) {
            this.cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        String identifier = args.getString(0);
                        String network = args.getString(1);
                        isCardInWallet(identifier,network, callbackContext);
                    } catch (Exception e) {
                        callbackContext.error(e.getMessage());
                    }
                }
            });

            return true;
        } else if("registerProvisioningCallback".equals(action)) {
            PluginResult result =
                    new PluginResult(
                            PluginResult.Status.NO_RESULT);

            result.setKeepCallback(true);

            callbackContext.sendPluginResult(result);

            return true;
        }else if("completeProvisioning".equals(action)){
            this.cordova.getThreadPool().execute(() ->
                    completeProvisioning(args, callbackContext)
            );
           return true;
        }

        callbackContext.error("\"" + action + "\" is not a recognized action.");

        return false;
    }

    /**
     * Returns the Wallet ID of the active wallet. If there is no active wallet, the status
     * TapAndPayStatusCodes.TAP_AND_PAY_NO_ACTIVE_WALLET is returned. A wallet can be created using the method createWallet.
     */
    private void getActiveWalletID(CallbackContext callbackContext) {
        Log.i(TAG, "getActiveWalletID");
        tapAndPayClient
                .getActiveWalletId()
                .addOnCompleteListener(
                        new OnCompleteListener<String>() {
                            @Override
                            public void onComplete(@NonNull Task<String> task) {
                                Log.i(TAG, "getActiveWalletID onComplete " + task.isSuccessful());
                                if (task.isSuccessful()) {
                                    String result = task.getResult();
                                    callbackContext.success(result);
                                } else {
                                    ApiException apiException = (ApiException) task.getException();
                                    String message = apiException.getMessage();
                                    int statusCode = apiException.getStatusCode();
                                    Log.i(TAG, "getActiveWalletID onComplete " + Integer.toString(statusCode));
                                    try {
                                        JSONObject value = new JSONObject();
                                        value.put("message", message);
                                        value.put("statusCode", statusCode);
                                        callbackContext.error(value);
                                    } catch (Exception e) {
                                        callbackContext.error(e.getMessage());
                                    }
                                }
                            }
                        });
    }

    /**
     * List tokens in the active wallet
     * <p>
     * The listTokens method will return a list of tokens in the Google Pay wallet.
     * Note that the API only returns token details for tokens with metadata
     * matching your app package name. You can check if your tokens have this
     * linking by tapping on the card in the Google Wallet app to see the card details view.
     */
    private void listTokens(CallbackContext callbackContext) {
        Log.i(TAG, "listTokens");
        tapAndPayClient
                .listTokens()
                .addOnCompleteListener(
                        new OnCompleteListener<List<TokenInfo>>() {
                            @Override
                            public void onComplete(@NonNull Task<List<TokenInfo>> task) {
                                Log.i(TAG, "listTokens onComplete " + task.isSuccessful());
                                if (task.isSuccessful()) {
                                    JSONArray result = new JSONArray();
                                    try {
                                        for (TokenInfo token : task.getResult()) {
                                            Log.d(TAG, "Found token with ID: " + token.getIssuerTokenId());
                                            JSONObject item = new JSONObject();
                                            item.put("issuerTokenId", token.getIssuerTokenId());
                                            item.put("issuerName", token.getIssuerName());
                                            item.put("fpanLastFour", token.getFpanLastFour());
                                            item.put("dpanLastFour", token.getDpanLastFour());
                                            item.put("tokenServiceProvider", token.getTokenServiceProvider());
                                            item.put("network", token.getNetwork());
                                            item.put("tokenState", token.getTokenState());
                                            item.put("isDefaultToken", token.getIsDefaultToken());
                                            item.put("portfolioName", token.getPortfolioName());

                                        }
                                        callbackContext.success(result);
                                    } catch (Exception e) {
                                        callbackContext.error(e.getMessage());
                                    }
                                } else {
                                    ApiException apiException = (ApiException) task.getException();
                                    String message = apiException.getMessage();
                                    int statusCode = apiException.getStatusCode();
                                    Log.i(TAG, "listTokens onComplete " + Integer.toString(statusCode));
                                    try {
                                        JSONObject value = new JSONObject();
                                        value.put("message", message);
                                        value.put("statusCode", statusCode);
                                        callbackContext.error(value);
                                    } catch (Exception e) {
                                        callbackContext.error(e.getMessage());
                                    }
                                }
                            }
                        });
    }

    /**
     * Each physical Android device has a stable hardware ID which is consistent between wallets for a given device. This ID will change as a result of a factory reset.
     * <p>
     * The stable hardware ID MAY ONLY be used for the following purposes:
     * - to encrypt inside OPC and provide back to Google Pay for push provisioning
     * - to make a risk decision (without storing the value) before the start of a Push Provisioning flow
     * <p>
     * The stable hardware ID received through the client API MUST NOT be used for the following purposes:
     * - stored by the issuer locally or at the backend
     * - track user activity
     * <p>
     * The stable hardware ID may not be accessed by the issuer outside the Push Provisioning flow.
     * <p>
     * Note: Make sure you have a Wallet ID before retrieving the stable hardware ID. If a wallet is not created yet,
     * the call to task.getResult() will return an empty String.
     * <p>
     * https://developers.google.com/pay/issuers/apis/push-provisioning/android/reading-wallet?authuser=2#getstablehardwareid
     */
    private void getStableHardwareId(CallbackContext callbackContext) {
        Log.i(TAG, "getStableHardwareId");
        tapAndPayClient
                .getStableHardwareId()
                .addOnCompleteListener(
                        new OnCompleteListener<String>() {
                            @Override
                            public void onComplete(@NonNull Task<String> task) {
                                Log.i(TAG, "getStableHardwareId onComplete " + task.isSuccessful());
                                if (task.isSuccessful()) {
                                    String hardwareId = task.getResult();
                                    if (hardwareId == "") {
                                        try {
                                            JSONObject value = new JSONObject();
                                            value.put("message", "No active wallet");
                                            value.put("statusCode", 15002);
                                            callbackContext.error(value);
                                        } catch (Exception e) {
                                            callbackContext.error(e.getMessage());
                                        }
                                    } else {
                                        callbackContext.success(hardwareId);
                                    }
                                } else {
                                    try {
                                        JSONObject value = new JSONObject();
                                        value.put("message", "Unknown error");
                                        value.put("statusCode", 0);
                                        callbackContext.error(value);
                                    } catch (Exception e) {
                                        callbackContext.error(e.getMessage());
                                    }
                                }
                            }
                        });
    }

    /**
     * Client-side push provisioning
     * <p>
     * pushTokenize starts the push tokenization flow in which the issuer provides most or all
     * card details needed for Google Pay to get a valid token. Tokens added using this method
     * are added to the active wallet.
     *
     * @param backendUrl  Backend Url for get OPC
     * @param displayName Name or nickname used to describe the payment card in the user interface
     * @param address     JSONObject containing optional string properties: name, address1, address2, localitiy, administrativeArea, countryCode, postalCode and phoneNumber
     * @param lastDigits  Last 4 digits for the payment card required to correctly display the card in Google Pay UI
     *                    <p>
     *                    https://developers.google.com/pay/issuers/apis/push-provisioning/android/wallet-operations?authuser=2#client-side_push_provisioning
     */
//    private void pushTokenize(String backendUrl, String token,String cardId, String displayName, String lastDigits, JSONObject address, String network, CallbackContext callbackContext) {
//
//        try {
//            // 1. Build the dynamic callback generator shown in your diagram
//            PaymentCredentialsGenerator credentialsGenerator = new PaymentCredentialsGenerator() {
//                @Override
//                public Future<GeneratePaymentCredentialsResponse> generate(GeneratePaymentCredentialsRequest request) {
//                    Log.d(TAG, "TapAndPay SDK triggered: Request issuer app fetch OPC");
//
//                    CompletableFuture<GeneratePaymentCredentialsResponse> completableFuture = new CompletableFuture<>();
//
//                    // Force execution off the Main Thread into Cordova's background pool
//                    cordova.getThreadPool().execute(new Runnable() {
//                        @Override
//                        public void run() {
//                            try {
//                                // Extract the runtime data parameters Google provides
//                                String walletId = request.getWalletId();
//                                String serverSessionId = request.getServerSessionId();
//                                boolean isGoogleOpcRequired = request.getGoogleOpaquePaymentCardRequested();
//
//                                // Execute direct native HTTP POST request to your Issuer Backend
//                                JSONObject backendResponse = fetchOpcDataFromBackend(
//                                        backendUrl, token, cardId, walletId, serverSessionId, isGoogleOpcRequired
//                                );
//
//                                // Extract the strings returned from your server
//                                String tspOpcBase64Str = backendResponse.getString("tspOpc");
//                                String googleOpcBase64Str = backendResponse.optString("googleOpc", "");
//
//                                // Decode standard RFC 4648 Non-URL-safe Base64 strings to bytes
//                                byte[] tspOpcBytes = Base64.decode(tspOpcBase64Str, Base64.DEFAULT);
//                                byte[] googleOpcBytes = null;
//
//                                if (isGoogleOpcRequired && !googleOpcBase64Str.isEmpty()) {
//                                    googleOpcBytes = Base64.decode(googleOpcBase64Str, Base64.DEFAULT);
//                                }
//
//                                // Build the required UAPP API model
//                                GeneratePaymentCredentialsResponse response =
//                                        new GeneratePaymentCredentialsResponse.Builder()
//                                                .setOpaquePaymentCard(tspOpcBytes)
//                                                .setGoogleOpaquePaymentCard(googleOpcBytes)
//                                                .build();
//
//                                // Resolve future to pass credentials back to TapAndPay SDK
//                                completableFuture.complete(response);
//                                Log.d(TAG, "OPCs successfully delivered back to TapAndPay SDK");
//
//                            } catch (Exception e) {
//                                Log.e(TAG, "Error matching diagram flow step: Fetch OPC failed", e);
//                                completableFuture.completeExceptionally(e);
//                            }
//                        }
//                    });
//
//                    return completableFuture;
//                }
//
//                @Override
//                public boolean getAuxiliaryOpaquePaymentCardSupported() {
//                    return false;
//                }
//
//                @Override
//                public List<String> getSupportedTokenRequestorIds() {
//                    return new ArrayList<>();
//                }
//            };
//
//
//            Log.i(TAG, "pushTokenize");
//
//            UserAddress.Builder builder = UserAddress.newBuilder();
//            if (address.has("name")) {
//                builder.setName(address.getString("name"));
//            }
//            if (address.has("address1")) {
//                builder.setAddress1(address.getString("address1"));
//            }
//            if (address.has("address2")) {
//                builder.setAddress2(address.getString("address2"));
//            }
//            if (address.has("locality")) {
//                builder.setLocality(address.getString("locality"));
//            }
//            if (address.has("administrativeArea")) {
//                builder.setAdministrativeArea(address.getString("administrativeArea"));
//            }
//            if (address.has("countryCode")) {
//                builder.setCountryCode(address.getString("countryCode"));
//            }
//            if (address.has("postalCode")) {
//                builder.setPostalCode(address.getString("postalCode"));
//            }
//            if (address.has("phoneNumber")) {
//                builder.setPhoneNumber(address.getString("phoneNumber"));
//                Log.d(TAG, "pushTokenize: Phone Number" + address.getString("phoneNumber"));
//            }
//
//            UserAddress userAddress = builder.build();
//            int cardNetwork;
//            int tokenServiceProvider;
//            if (network.equalsIgnoreCase("VISA")) {
//                cardNetwork = TapAndPay.CARD_NETWORK_VISA;
//                tokenServiceProvider = TapAndPay.TOKEN_PROVIDER_VISA;
//            } else {
//                cardNetwork = TapAndPay.CARD_NETWORK_MASTERCARD;
//                tokenServiceProvider = TapAndPay.TOKEN_PROVIDER_MASTERCARD;
//            }
//
//            PushTokenizeRequest pushTokenizeRequest =
//                    new PushTokenizeRequest.Builder()
//                            .setNetwork(cardNetwork)
//                            .setTokenServiceProvider(tokenServiceProvider)
//                            .setDisplayName(displayName)
//                            .setLastDigits(lastDigits)
//                            .setUserAddress(userAddress)
//                            .setPaymentCredentialsGenerator(credentialsGenerator)
//                            .build();
//
//            cordova.setActivityResultCallback(this);
//
//            tapAndPayClient.pushTokenize(this.cordova.getActivity(), pushTokenizeRequest, REQUEST_CODE_PUSH_TOKENIZE);
//        } catch (Exception e) {
//            Log.e(TAG, "pushTokenize error", e);
//            callbackContext.error(e.getMessage());
//        }
//    }

    private final ConcurrentHashMap<String, PendingProvisionRequest> pendingRequests =
            new ConcurrentHashMap<>();

    private void pushTokenize( String displayName, String lastDigits, JSONObject address, String network, CallbackContext callbackContext) {
        // Save this context globally within the class so your onActivityResult method
        // can send the final "SUCCESS" or "CANCELED" events back to the same callback channel.
        this.pushTokenizeCallbackContext = callbackContext;
        try {
            // 1. Build the dynamic callback generator shown in your diagram
            PaymentCredentialsGenerator credentialsGenerator = new PaymentCredentialsGenerator() {
                @Override
                public Future<GeneratePaymentCredentialsResponse> generate(GeneratePaymentCredentialsRequest request) {
                    Log.d(TAG, "TapAndPay SDK triggered: Request issuer app fetch OPC");

                    CompletableFuture<GeneratePaymentCredentialsResponse> future = new CompletableFuture<>();
                    String sessionId = request.getServerSessionId();

                    pendingRequests.put(
                            sessionId,
                            new PendingProvisionRequest(future, request)
                    );
                    try {

                        JSONObject event = new JSONObject();
                        event.put("type", "NEED_OPC");
                        event.put("walletId", request.getWalletId());
                        event.put("serverSessionId", sessionId);
                        event.put("googleOpcRequired", request.getGoogleOpaquePaymentCardRequested());

                        PluginResult result = new PluginResult(PluginResult.Status.OK, event);
                        result.setKeepCallback(true); // Keep the callback channel open for JavaScript
                        callbackContext.sendPluginResult(result);

                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }

                    return future;
                }

                @Override
                public boolean getAuxiliaryOpaquePaymentCardSupported() {
                    return true;
                }

                @Override
                public List<String> getSupportedTokenRequestorIds() {
                    // CRITICAL FIX: Cannot be empty. Must include Google Pay's Token Requestor ID.
                    List<String> requestorIds = new ArrayList<>();
//                    requestorIds.add(TapAndPay.TAP_AND_PAY_TOKEN_REQUESTOR_ID_GOOGLE_PAY);
                    requestorIds.add("40010030273");
                    return requestorIds;
                }
            };


            Log.i(TAG, "pushTokenize");

            UserAddress.Builder builder = UserAddress.newBuilder();
            if (address.has("name")) {
                builder.setName(address.getString("name"));
            }
            if (address.has("address1")) {
                builder.setAddress1(address.getString("address1"));
            }
            if (address.has("address2")) {
                builder.setAddress2(address.getString("address2"));
            }
            if (address.has("locality")) {
                builder.setLocality(address.getString("locality"));
            }
            if (address.has("administrativeArea")) {
                builder.setAdministrativeArea(address.getString("administrativeArea"));
            }
            if (address.has("countryCode")) {
                builder.setCountryCode(address.getString("countryCode"));
            }
            if (address.has("postalCode")) {
                builder.setPostalCode(address.getString("postalCode"));
            }
            if (address.has("phoneNumber")) {
                builder.setPhoneNumber(address.getString("phoneNumber"));
                Log.d(TAG, "pushTokenize: Phone Number" + address.getString("phoneNumber"));
            }

            UserAddress userAddress = builder.build();
            int cardNetwork;
            int tokenServiceProvider;
            if (network.equalsIgnoreCase("VISA")) {
                cardNetwork = TapAndPay.CARD_NETWORK_VISA;
                tokenServiceProvider = TapAndPay.TOKEN_PROVIDER_VISA;
            } else {
                cardNetwork = TapAndPay.CARD_NETWORK_MASTERCARD;
                tokenServiceProvider = TapAndPay.TOKEN_PROVIDER_MASTERCARD;
            }

            PushTokenizeRequest pushTokenizeRequest =
                    new PushTokenizeRequest.Builder()
                            .setNetwork(cardNetwork)
                            .setTokenServiceProvider(tokenServiceProvider)
                            .setDisplayName(displayName)
                            .setLastDigits(lastDigits)
                            .setUserAddress(userAddress)
                            .setPaymentCredentialsGenerator(credentialsGenerator)
                            .build();

            cordova.setActivityResultCallback(this);

            tapAndPayClient.pushTokenize(this.cordova.getActivity(), pushTokenizeRequest, REQUEST_CODE_PUSH_TOKENIZE);
        } catch (Exception e) {
            Log.e(TAG, "pushTokenize error", e);
            callbackContext.error(e.getMessage());
        }
    }

    private void completeProvisioning(
            JSONArray args,
            CallbackContext callbackContext) {

        try {

            String sessionId = args.getString(0);
            String tspOpc = args.getString(1);
            String googleOpc = args.optString(2, "");

            Log.d("completeProvisioning","sessionId: " + sessionId + " tspOpc: " + tspOpc + " googleOpc: " + googleOpc);

            PendingProvisionRequest pending = pendingRequests.remove(sessionId);

            if (pending == null) {
                Log.d("GoogleWalletPlugin", "Session not found or already expired");
                callbackContext.error("Session not found or already expired");
                return;
            }

            // CRITICAL FIX: Use NO_WRAP instead of DEFAULT to prevent whitespace/newline decoding bugs
            // byte[] tsp = Base64.decode(tspOpc, Base64.NO_WRAP);
            byte[] tsp = tspOpc.getBytes(StandardCharsets.UTF_8);
            byte[] google = null;

            if (!googleOpc.isEmpty()) {
                // google = Base64.decode(googleOpc, Base64.NO_WRAP);
                google = googleOpc.getBytes(StandardCharsets.UTF_8);
            }

            GeneratePaymentCredentialsResponse.Builder builder =
                    new GeneratePaymentCredentialsResponse.Builder()
                            .setOpaquePaymentCard(tsp);

            if (google != null) {
                builder.setGoogleOpaquePaymentCard(google);
            }

            // Deliver the credentials back to the waiting Google SDK future
            pending.future.complete(builder.build());
            callbackContext.success();
        } catch (Exception e) {
            Log.e(TAG, "completeProvisioning error", e);
            callbackContext.error(e.getMessage());
        }
    }

    /**
     * Card lookup by last 4 FPAN digits
     * Please note this endpoint can return false positives since the last four FPAN digits are not necessarily unique among tokens.
     *
     * @param identifier last 4 FPAN (Last 4 card digits)
     *                   <p>
     *                   https://developers.google.com/pay/issuers/apis/push-provisioning/android/reading-wallet?authuser=2#istokenized
     */
    private void isCardInWallet(String identifier, String network, CallbackContext callbackContext) {
        int cardNetwork;
        int tokenServiceProvider;
        if (network.equalsIgnoreCase("VISA")) {
            cardNetwork = TapAndPay.CARD_NETWORK_VISA;
            tokenServiceProvider = TapAndPay.TOKEN_PROVIDER_VISA;
        } else {
            cardNetwork = TapAndPay.CARD_NETWORK_MASTERCARD;
            tokenServiceProvider = TapAndPay.TOKEN_PROVIDER_MASTERCARD;
        }

        IsTokenizedRequest request = new IsTokenizedRequest.Builder()
                .setIdentifier(identifier)
                .setNetwork(cardNetwork)
                .setTokenServiceProvider(tokenServiceProvider)
                .build();
        tapAndPayClient
                .isTokenized(request)
                .addOnCompleteListener(
                        new OnCompleteListener<Boolean>() {
                            @Override
                            public void onComplete(@NonNull Task<Boolean> task) {
                                if (task.isSuccessful()) {
                                    if (task.getResult()) {
                                        Log.d(TAG, "Found a token with last four digits " + identifier + ".");
                                        try {
                                            JSONObject value = new JSONObject();
                                            value.put("result", "token found");
                                            callbackContext.success(value);
                                        } catch (Exception e) {
                                            callbackContext.error(e.getMessage());
                                        }
                                    } else {
                                        try {
                                            JSONObject value = new JSONObject();
                                            value.put("result", "token not found");
                                            callbackContext.success(value);
                                        } catch (Exception e) {
                                            callbackContext.error(e.getMessage());
                                        }
                                    }
                                } else {
                                    ApiException apiException = (ApiException) task.getException();
                                    String message = apiException.getMessage();
                                    int statusCode = apiException.getStatusCode();
                                    Log.i(TAG, "listTokens onComplete " + Integer.toString(statusCode));
                                    try {
                                        JSONObject value = new JSONObject();
                                        value.put("message", message);
                                        value.put("statusCode", statusCode);
                                        callbackContext.error(value);
                                    } catch (Exception e) {
                                        callbackContext.error(e.getMessage());
                                    }
                                }
                            }
                        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "onActivityResult resultCode: " + resultCode + ", requestCode: " + requestCode);
        super.onActivityResult(requestCode, resultCode, data);
        // Push provisioning
        if (requestCode == REQUEST_CODE_PUSH_TOKENIZE) {
            if (resultCode == RESULT_CANCELED) {
                // The user canceled the request.
                try {
                    JSONObject value = new JSONObject();
                    value.put("type", "canceled");
                    Log.e(TAG, "onActivityResult error RESULT_CANCELED");
                    pushTokenizeCallbackContext.error(value);
                } catch (Exception e) {
                    Log.e(TAG, "onActivityResult error RESULT_CANCELED JSON exception", e);
                    pushTokenizeCallbackContext.error(e.getMessage());
                }

                if (data != null && data.getExtras() != null) {
                    Bundle extras = data.getExtras();
                    for (String key : extras.keySet()) {
                        Log.d(TAG, "Error Extra: " + key + " = " + extras.get(key));
                    }
                } else {
                    Log.d(TAG, "onActivityResult: data or extras is null");
                }

                return;
            } else if (resultCode == RESULT_OK) {
                // The action succeeded.
                String tokenId = data.getStringExtra(TapAndPay.EXTRA_ISSUER_TOKEN_ID);
                try {
                    JSONObject value = new JSONObject();
                    value.put("type", "result");
                    value.put("tokenId", tokenId);
                    Log.i(TAG, "onActivityResult ok tokenId:" + tokenId);
                    pushTokenizeCallbackContext.success(value);
                } catch (Exception e) {
                    Log.e(TAG, "onActivityResult error RESULT_OK JSON exception", e);
                    pushTokenizeCallbackContext.error(e.getMessage());
                }
                return;
            } else {
                Log.e(TAG, "onActivityResult error unknown resultCode: " + Integer.toString(resultCode));
                pushTokenizeCallbackContext.error(resultCode);
            }
        }
    }

    // Native networking helper method running within the Generator Thread
    private JSONObject fetchOpcDataFromBackend(String urlStr, String token, String cardId,
                                               String walletId, String sessionId, boolean googleOpcReq) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; utf-8");
        conn.setRequestProperty("Authorization", "Bearer " + token); // If required by your API Gateway
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000); // 10 Second timeout window maximum
        conn.setReadTimeout(10000);

        // Build the outgoing API schema containing Google's session contexts
        JSONObject jsonPayload = new JSONObject();
        jsonPayload.put("cardId", cardId);
        jsonPayload.put("walletId", walletId);
        jsonPayload.put("serverSessionId", sessionId);
        jsonPayload.put("googleOpcRequested", googleOpcReq);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonPayload.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Backend HTTP error code: " + code);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return new JSONObject(response.toString());
        }
    }
}

