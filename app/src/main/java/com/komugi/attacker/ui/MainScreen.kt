package com.komugi.attacker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.komugi.attacker.MainViewModel
import com.komugi.attacker.ble.PayloadFamily
import com.komugi.attacker.ble.SpamConfig
import com.komugi.attacker.ble.SpamMode
import com.komugi.attacker.ble.TargetDevice
import com.komugi.attacker.ui.theme.GoogleSans
import com.komugi.attacker.ui.theme.MonoFamily
import com.komugi.attacker.ui.theme.Success
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenScan: () -> Unit = {},
) {
    val snapshot by viewModel.snapshot.collectAsState()
    var showTargetSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uptime = rememberUptime(snapshot.startedAtMillis, snapshot.running)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bluetooth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "KomugiAttacker",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (snapshot.running) "engine live" else "idle",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (snapshot.running) Success
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            ActionBar(
                running = snapshot.running,
                mode = viewModel.config.mode,
                onStart = onStart,
                onStop = onStop,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroCard(snapshot.running, snapshot.packets, snapshot.failures, uptime)

            ModeSelector(viewModel.config.mode) { viewModel.setMode(it) }

            TargetCard(
                mode = viewModel.config.mode,
                targetName = viewModel.config.targetName,
                targetAddress = viewModel.config.targetAddress,
                onOpenScan = {
                    onOpenScan()
                    showTargetSheet = true
                },
                onClear = { viewModel.selectTarget(null) },
            )

            if (viewModel.config.mode == SpamMode.ADVERTISE) {
                PayloadCard(
                    family = viewModel.config.family,
                    cycleAll = viewModel.config.cycleAllFamilies,
                    intervalMs = viewModel.config.intervalMs,
                    customHex = viewModel.customHex,
                    customHexError = viewModel.customHexError,
                    onFamily = { viewModel.setFamily(it) },
                    onCycleAll = { viewModel.setCycleAll(it) },
                    onInterval = { viewModel.setInterval(it) },
                    onCustomHex = { viewModel.updateCustomHex(it) },
                )
            } else {
                FloodCard(
                    threads = viewModel.config.threads,
                    onThreads = { viewModel.setThreads(it) },
                )
            }

            LogCard(snapshot.log, snapshot.running)

            Spacer(Modifier.height(4.dp))
            Text(
                text = "KomugiAttacker В· BLE link pressure rig",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showTargetSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showTargetSheet = false
                viewModel.stopScan()
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            TargetSheet(
                targets = viewModel.targets,
                scanning = viewModel.scanning,
                selected = viewModel.config.targetAddress,
                onRefresh = onOpenScan,
                onSelect = {
                    viewModel.selectTarget(it)
                    viewModel.stopScan()
                    showTargetSheet = false
                },
            )
        }
    }
}

@Composable
private fun HeroCard(running: Boolean, packets: Long, failures: Long, uptimeSeconds: Long) {
    val containerColor by animateColorAsState(
        targetValue = if (running) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceContainer,
        label = "hero",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.0f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                        )
                    )
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PulseBadge(running)
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (running) "Broadcasting" else "Standing by",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (running) "Payloads cycling in the background"
                else "Pick a mode, pick a target, hit start",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Metric("Packets", formatCount(packets), Icons.Rounded.Speed)
                Metric("Failures", formatCount(failures), Icons.Rounded.ErrorOutline)
                Metric("Uptime", formatUptime(uptimeSeconds), Icons.Rounded.Timer)
            }
        }
    }
}

@Composable
private fun PulseBadge(running: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "phase",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(148.dp)) {
        if (running) {
            listOf(phase, (phase + 0.5f) % 1f).forEach { p ->
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(1f + p * 0.55f)
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = (1f - p) * 0.45f),
                            shape = CircleShape,
                        )
                )
            }
        }
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    if (running) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (running) Icons.Rounded.Radar else Icons.AutoMirrored.Rounded.BluetoothSearching,
                contentDescription = null,
                tint = if (running) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeSelector(mode: SpamMode, onMode: (SpamMode) -> Unit) {
    val modes = SpamMode.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = mode == item,
                    onClick = { onMode(item) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    icon = {
                        Icon(
                            imageVector = if (item == SpamMode.ADVERTISE) Icons.Rounded.Radar
                            else Icons.Rounded.BluetoothConnected,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                ) {
                    Text(item.title)
                }
            }
        }
        Text(
            text = mode.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun TargetCard(
    mode: SpamMode,
    targetName: String?,
    targetAddress: String?,
    onOpenScan: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("Target", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = onOpenScan) {
                    Icon(
                        imageVector = Icons.Rounded.Radar,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Scan")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = when {
                    targetAddress != null -> targetName ?: "Unknown device"
                    mode == SpamMode.ADVERTISE -> "Every device in range"
                    else -> "No target вЂ” flood follows anything the scanner sees"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (targetAddress != null) {
                Text(
                    text = targetAddress,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onClear, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("Clear target")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PayloadCard(
    family: PayloadFamily,
    cycleAll: Boolean,
    intervalMs: Int,
    customHex: String,
    customHexError: String?,
    onFamily: (PayloadFamily) -> Unit,
    onCycleAll: (Boolean) -> Unit,
    onInterval: (Int) -> Unit,
    onCustomHex: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("Payload", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PayloadFamily.entries.forEach { entry ->
                    FilterChip(
                        selected = family == entry,
                        onClick = { onFamily(entry) },
                        label = { Text(entry.title) },
                        leadingIcon = if (family == entry) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                }
            }

            if (family == PayloadFamily.CUSTOM) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = customHex,
                    onValueChange = onCustomHex,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Manufacturer payload hex") },
                    placeholder = { Text("4C000719070220...") },
                    isError = customHexError != null,
                    supportingText = {
                        Text(customHexError ?: "Company id first, then payload bytes")
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                    minLines = 2,
                    maxLines = 4,
                )
            }

            if (family == PayloadFamily.APPLE) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Cycle every family", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Apple, TV setup and Swift Pair in one stream",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = cycleAll, onCheckedChange = onCycleAll)
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Interval", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Text(
                    "${intervalMs} ms",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            androidx.compose.material3.Slider(
                value = intervalMs.toFloat(),
                onValueChange = { onInterval(it.toInt()) },
                valueRange = SpamConfig.MIN_INTERVAL.toFloat()..SpamConfig.MAX_INTERVAL.toFloat(),
                steps = 0,
            )
            Text(
                text = if (intervalMs <= 60) "Aggressive вЂ” controller may throttle"
                else "Lower is faster, higher is gentler on the radio",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FloodCard(threads: Int, onThreads: (Int) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("Pressure", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    "$threads workers",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "Each worker alternates RFCOMM channel connects and GATT connects, aborts the link, repeats.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.Slider(
                value = threads.toFloat(),
                onValueChange = { onThreads(it.toInt()) },
                valueRange = SpamConfig.MIN_THREADS.toFloat()..SpamConfig.MAX_THREADS.toFloat(),
                steps = SpamConfig.MAX_THREADS - SpamConfig.MIN_THREADS - 1,
            )
        }
    }
}

@Composable
private fun LogCard(log: List<String>, running: Boolean) {
    val listState = rememberLazyListState()
    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Live log", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                if (running) {
                    LinearProgressIndicator(
                        modifier = Modifier.width(46.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${log.size} lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(10.dp),
            ) {
                if (log.isEmpty()) {
                    Text(
                        text = "no events yet",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    LazyColumn(state = listState) {
                        items(log) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = MonoFamily,
                                    fontSize = 11.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetSheet(
    targets: List<TargetDevice>,
    scanning: Boolean,
    selected: String?,
    onRefresh: () -> Unit,
    onSelect: (TargetDevice?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pick a target", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = if (scanning) Icons.Rounded.Refresh else Icons.AutoMirrored.Rounded.BluetoothSearching,
                    contentDescription = "Rescan",
                )
            }
        }
        Text(
            text = if (scanning) "scanningвЂ¦" else "${targets.size} devices cached",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Surface(
            color = if (selected == null) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(null) },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == null, onClick = { onSelect(null) })
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("Broadcast", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Advertise mode: every device in range",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
            items(targets, key = { it.address }) { device ->
                Surface(
                    color = if (device.address == selected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSelect(device) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = device.address == selected,
                            onClick = { onSelect(device) },
                        )
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                device.label,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                device.address,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = if (device.isBonded) "Paired" else "${device.rssi} dBm",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (device.isBonded) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionBar(running: Boolean, mode: SpamMode, onStart: () -> Unit, onStop: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = { if (running) onStop() else onStart() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primary,
                    contentColor = if (running) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when {
                        running -> "Stop engine"
                        mode == SpamMode.ADVERTISE -> "Start broadcast"
                        else -> "Start flood"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun rememberUptime(startedAt: Long, running: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt, running) {
        while (running) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return if (startedAt <= 0L) 0 else ((now - startedAt) / 1000L).coerceAtLeast(0)
}

private fun formatCount(value: Long): String = when {
    value < 1000 -> value.toString()
    value < 1_000_000 -> String.format(Locale.US, "%.1fk", value / 1000.0)
    else -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
}

private fun formatUptime(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, secs)
}
