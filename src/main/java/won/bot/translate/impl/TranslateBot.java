package won.bot.translate.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import won.bot.framework.bot.base.EventBot;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.BaseEventBotAction;
import won.bot.framework.eventbot.behaviour.ExecuteWonMessageCommandBehaviour;
import won.bot.framework.eventbot.bus.EventBus;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.impl.command.connect.ConnectCommandEvent;
import won.bot.framework.eventbot.event.impl.command.connect.ConnectCommandResultEvent;
import won.bot.framework.eventbot.event.impl.command.connect.ConnectCommandSuccessEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.ConnectFromOtherAtomEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.MessageFromOtherAtomEvent;
import won.bot.framework.eventbot.filter.impl.AtomUriInNamedListFilter;
import won.bot.framework.eventbot.filter.impl.CommandResultFilter;
import won.bot.framework.eventbot.filter.impl.NotFilter;
import won.bot.framework.eventbot.listener.EventListener;
import won.bot.framework.eventbot.listener.impl.ActionOnEventListener;
import won.bot.framework.eventbot.listener.impl.ActionOnFirstEventListener;
import won.bot.framework.extensions.matcher.MatcherBehaviour;
import won.bot.framework.extensions.matcher.MatcherExtension;
import won.bot.framework.extensions.serviceatom.ServiceAtomBehaviour;
import won.bot.framework.extensions.serviceatom.ServiceAtomExtension;
import won.bot.translate.action.ReplyAction;
import won.bot.translate.api.TranslatorAPI;
import won.bot.translate.context.TranslateBotContextWrapper;

import java.lang.invoke.MethodHandles;

public class TranslateBot extends EventBot implements MatcherExtension, ServiceAtomExtension {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private int registrationMatcherRetryInterval;
    private MatcherBehaviour matcherBehaviour;
    private ServiceAtomBehaviour serviceAtomBehaviour;

    // bean setter, used by spring
    public void setRegistrationMatcherRetryInterval(final int registrationMatcherRetryInterval) {
        this.registrationMatcherRetryInterval = registrationMatcherRetryInterval;
    }

    @Override
    public ServiceAtomBehaviour getServiceAtomBehaviour() {
        return serviceAtomBehaviour;
    }

    @Override
    public MatcherBehaviour getMatcherBehaviour() {
        return matcherBehaviour;
    }

    @Override
    protected void initializeEventListeners() {
        EventListenerContext ctx = getEventListenerContext();

        if (!(getBotContextWrapper() instanceof TranslateBotContextWrapper)) {
            logger.error(getBotContextWrapper().getBotName() + " does not work without a TranslateBotContextWrapper");
            throw new IllegalStateException(getBotContextWrapper().getBotName() + " does not work without a TranslateBotContextWrapper");
        }
        EventBus bus = getEventBus();
        TranslateBotContextWrapper botContextWrapper = (TranslateBotContextWrapper) getBotContextWrapper();

        // register listeners for event.impl.command events used to tell the bot to send
        // messages
        ExecuteWonMessageCommandBehaviour wonMessageCommandBehaviour = new ExecuteWonMessageCommandBehaviour(ctx);
        wonMessageCommandBehaviour.activate();

        // activate ServiceAtomBehaviour
        serviceAtomBehaviour = new ServiceAtomBehaviour(ctx);
        serviceAtomBehaviour.activate();

        // set up matching extension
        // as this is an extension, it can be activated and deactivated as needed
        // if activated, a MatcherExtensionAtomCreatedEvent is sent every time a new atom is created on a monitored node
        matcherBehaviour = new MatcherBehaviour(ctx, "TranslateBotMatchingExtension", registrationMatcherRetryInterval);
        matcherBehaviour.activate();

        // create filters to determine which atoms the bot should react to
        NotFilter noOwnAtoms = new NotFilter(new AtomUriInNamedListFilter(ctx, ctx.getBotContextWrapper().getAtomCreateListName()));
        // filter to prevent reacting to serviceAtom<->ownedAtom events;
        NotFilter noInternalServiceAtomEventFilter = getNoInternalServiceAtomEventFilter();



        bus.subscribe(MessageFromOtherAtomEvent.class,
                new ActionOnEventListener(ctx, "ReceivedTextMessage", new ReplyAction(ctx)));

        bus.subscribe(ConnectFromOtherAtomEvent.class, noInternalServiceAtomEventFilter, new BaseEventBotAction(ctx) {
            @Override
            protected void doRun(Event event, EventListener executingListener) {
                EventListenerContext ctx = getEventListenerContext();

                ConnectFromOtherAtomEvent connectFromOtherAtomEvent = (ConnectFromOtherAtomEvent) event;
                try {
                    String receivedConnectMessage = ReplyAction.extractTextMessageFromWonMessage(((ConnectFromOtherAtomEvent) event).getWonMessage());

                    final ConnectCommandEvent connectCommandEvent = new ConnectCommandEvent(connectFromOtherAtomEvent.getRecipientSocket(), connectFromOtherAtomEvent.getSenderSocket(), TranslatorAPI.handleRequest(receivedConnectMessage));
                    ctx.getEventBus().subscribe(ConnectCommandSuccessEvent.class, new ActionOnFirstEventListener(ctx,
                                                                                                          new CommandResultFilter(connectCommandEvent), new BaseEventBotAction(ctx) {
                        @Override
                        protected void doRun(Event event, EventListener executingListener) {
                            ConnectCommandResultEvent connectionMessageCommandResultEvent = (ConnectCommandResultEvent) event;
                            if (!connectionMessageCommandResultEvent.isSuccess()) {
                                logger.error("Failure when trying to open a received Request: " + connectionMessageCommandResultEvent.getMessage());
                            } else {
                                logger.info(
                                    "Add an established connection " +
                                    connectCommandEvent.getLocalSocket() + " -> " + connectCommandEvent.getTargetSocket() +
                                    " to the botcontext "
                                );
                                botContextWrapper.addConnectedSocket(connectCommandEvent.getLocalSocket(), connectCommandEvent.getTargetSocket());
                            }
                        }
                    }));
                    ctx.getEventBus().publish(connectCommandEvent);
                } catch (Exception te) {
                    logger.error(te.getMessage(), te);
                }
            }
        });
    }
}
