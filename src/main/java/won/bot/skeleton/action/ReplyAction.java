package won.bot.skeleton.action;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.BaseEventBotAction;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.impl.command.connectionmessage.ConnectionMessageCommandEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.MessageFromOtherAtomEvent;
import won.bot.framework.eventbot.listener.EventListener;
import won.bot.skeleton.api.TranslatorAPI;
import won.bot.skeleton.context.SkeletonBotContextWrapper;
import won.protocol.message.WonMessage;
import won.protocol.model.Connection;
import won.protocol.util.WonRdfUtils;

import java.lang.invoke.MethodHandles;
import java.net.URI;

/**
 * Created by MS on 24.09.2018.
 */
public class ReplyAction extends BaseEventBotAction {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public ReplyAction(EventListenerContext ctx) {
        super(ctx);
    }

    public static String extractTextMessageFromWonMessage(WonMessage wonMessage) {
        if (wonMessage == null)
            return null;
        return WonRdfUtils.MessageUtils.getTextMessage(wonMessage);
    }

    @Override
    protected void doRun(Event event, EventListener executingListener) throws Exception {
        logger.info("MessageEvent received");
        EventListenerContext ctx = getEventListenerContext();
        if (event instanceof MessageFromOtherAtomEvent
                && ctx.getBotContextWrapper() instanceof SkeletonBotContextWrapper) {
            SkeletonBotContextWrapper botContextWrapper = (SkeletonBotContextWrapper) ctx.getBotContextWrapper();
            Connection con = ((MessageFromOtherAtomEvent) event).getCon();
            URI yourAtomUri = con.getAtomURI();

            String sourceMessage = extractTextMessageFromWonMessage(((MessageFromOtherAtomEvent) event).getWonMessage());
            String respondWith = TranslatorAPI.handleRequest(sourceMessage);

            try {
                getEventListenerContext().getEventBus().publish(new ConnectionMessageCommandEvent(con, respondWith));
            } catch (Exception te) {
                logger.error(te.getMessage());
            }
        }
    }
}
